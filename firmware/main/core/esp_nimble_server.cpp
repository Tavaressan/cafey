#if defined(ESP_PLATFORM)

#include "core/esp_nimble_server.hpp"

#include <cctype>
#include <cstring>

#include "esp_log.h"
#include "nimble/nimble_port.h"
#include "nimble/nimble_port_freertos.h"
#include "host/ble_hs.h"
#include "host/util/util.h"
#include "services/gap/ble_svc_gap.h"
#include "services/gatt/ble_svc_gatt.h"

namespace cafey::core {

namespace {

constexpr const char* TAG = "ble_srv";

// Converte "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" em ble_uuid128_t (a NimBLE
// guarda os 16 bytes em ordem little-endian).
bool parse_uuid128(const std::string& text, ble_uuid128_t& out) {
    uint8_t big_endian[16] = {0};
    int nibbles = 0;
    for (char ch : text) {
        if (ch == '-') continue;
        if (!std::isxdigit(static_cast<unsigned char>(ch))) return false;
        if (nibbles >= 32) return false;
        uint8_t v = static_cast<uint8_t>(std::isdigit(static_cast<unsigned char>(ch))
                                             ? ch - '0'
                                             : std::tolower(ch) - 'a' + 10);
        uint8_t& byte = big_endian[nibbles / 2];
        byte = static_cast<uint8_t>((byte << 4) | v);
        ++nibbles;
    }
    if (nibbles != 32) return false;

    out.u.type = BLE_UUID_TYPE_128;
    for (int i = 0; i < 16; ++i) {
        out.value[i] = big_endian[15 - i];
    }
    return true;
}

} // namespace

EspNimbleServer* EspNimbleServer::self_ = nullptr;

EspNimbleServer::EspNimbleServer(const std::string& service_uuid) {
    parse_uuid128(service_uuid, service_uuid_);
    self_ = this;
}

EspNimbleServer::~EspNimbleServer() {
    if (self_ == this) self_ = nullptr;
}

void EspNimbleServer::add_characteristic(const std::string& uuid, uint8_t props,
                                         BleWriteCallback on_write) {
    Entry entry;
    if (!parse_uuid128(uuid, entry.uuid)) {
        ESP_LOGE(TAG, "UUID de caracteristica invalido: %s", uuid.c_str());
        return;
    }
    entry.props = props;
    entry.on_write = std::move(on_write);
    entries_.push_back(std::move(entry));
}

int EspNimbleServer::access_cb(uint16_t /*conn_handle*/, uint16_t attr_handle,
                               struct ble_gatt_access_ctxt* ctxt, void* /*arg*/) {
    EspNimbleServer* server = self_;
    if (server == nullptr) return BLE_ATT_ERR_UNLIKELY;

    for (Entry& entry : server->entries_) {
        if (entry.val_handle != attr_handle) continue;

        switch (ctxt->op) {
            case BLE_GATT_ACCESS_OP_READ_CHR: {
                int rc = os_mbuf_append(ctxt->om, entry.value.data(),
                                        static_cast<uint16_t>(entry.value.size()));
                return rc == 0 ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
            }
            case BLE_GATT_ACCESS_OP_WRITE_CHR: {
                std::string received;
                received.resize(OS_MBUF_PKTLEN(ctxt->om));
                uint16_t copied = 0;
                ble_hs_mbuf_to_flat(ctxt->om, received.data(),
                                    static_cast<uint16_t>(received.size()), &copied);
                received.resize(copied);
                if (entry.on_write) entry.on_write(received);
                return 0;
            }
            default:
                return BLE_ATT_ERR_UNLIKELY;
        }
    }
    return BLE_ATT_ERR_UNLIKELY;
}

bool EspNimbleServer::update_value(const std::string& uuid, const std::string& value) {
    ble_uuid128_t target;
    if (!parse_uuid128(uuid, target)) return false;

    for (Entry& entry : entries_) {
        if (std::memcmp(entry.uuid.value, target.value, 16) != 0) continue;
        entry.value = value;
        if (started_ && (entry.props & kBleNotify)) {
            struct os_mbuf* om = ble_hs_mbuf_from_flat(value.data(),
                                                       static_cast<uint16_t>(value.size()));
            if (om != nullptr) {
                ble_gatts_notify_custom(BLE_HS_CONN_HANDLE_NONE, entry.val_handle, om);
            }
        }
        return true;
    }
    return false;
}

void EspNimbleServer::advertise() {
    struct ble_gap_adv_params adv_params{};
    adv_params.conn_mode = BLE_GAP_CONN_MODE_UND;
    adv_params.disc_mode = BLE_GAP_DISC_MODE_GEN;

    struct ble_hs_adv_fields fields{};
    fields.flags = BLE_HS_ADV_F_DISC_GEN | BLE_HS_ADV_F_BREDR_UNSUP;
    fields.name = reinterpret_cast<const uint8_t*>(device_name_.c_str());
    fields.name_len = static_cast<uint8_t>(device_name_.size());
    fields.name_is_complete = 1;
    fields.uuids128 = &service_uuid_;
    fields.num_uuids128 = 1;
    fields.uuids128_is_complete = 1;
    ble_gap_adv_set_fields(&fields);

    ble_gap_adv_start(own_addr_type_, nullptr, BLE_HS_FOREVER, &adv_params,
                      &EspNimbleServer::gap_event, nullptr);
}

int EspNimbleServer::gap_event(struct ble_gap_event* event, void* /*arg*/) {
    if (self_ == nullptr) return 0;
    switch (event->type) {
        case BLE_GAP_EVENT_CONNECT:
            if (event->connect.status != 0) self_->advertise();
            break;
        case BLE_GAP_EVENT_DISCONNECT:
            self_->advertise();
            break;
        default:
            break;
    }
    return 0;
}

void EspNimbleServer::on_sync() {
    if (self_ == nullptr) return;
    ble_hs_util_ensure_addr(0);
    ble_hs_id_infer_auto(0, &self_->own_addr_type_);
    self_->advertise();
}

void EspNimbleServer::host_task(void* /*param*/) {
    nimble_port_run();
    nimble_port_freertos_deinit();
}

bool EspNimbleServer::start(const std::string& device_name) {
    device_name_ = device_name;

    if (nimble_port_init() != ESP_OK) {
        ESP_LOGE(TAG, "nimble_port_init falhou");
        return false;
    }

    ble_hs_cfg.sync_cb = &EspNimbleServer::on_sync;

    ble_svc_gap_init();
    ble_svc_gatt_init();
    ble_svc_gap_device_name_set(device_name_.c_str());

    // Monta a definicao GATT: um servico primario com todas as caracteristicas.
    chr_defs_.clear();
    chr_defs_.reserve(entries_.size() + 1);
    for (Entry& entry : entries_) {
        ble_gatt_chr_def def{};
        def.uuid = &entry.uuid.u;
        def.access_cb = &EspNimbleServer::access_cb;
        def.val_handle = &entry.val_handle;
        ble_gatt_chr_flags flags = 0;
        if (entry.props & kBleRead) flags |= BLE_GATT_CHR_F_READ;
        if (entry.props & kBleWrite) flags |= BLE_GATT_CHR_F_WRITE;
        if (entry.props & kBleNotify) flags |= BLE_GATT_CHR_F_NOTIFY;
        def.flags = flags;
        chr_defs_.push_back(def);
    }
    chr_defs_.push_back(ble_gatt_chr_def{});  // terminador

    svc_defs_[0] = ble_gatt_svc_def{};
    svc_defs_[0].type = BLE_GATT_SVC_TYPE_PRIMARY;
    svc_defs_[0].uuid = &service_uuid_.u;
    svc_defs_[0].characteristics = chr_defs_.data();
    svc_defs_[1] = ble_gatt_svc_def{};  // terminador

    if (ble_gatts_count_cfg(svc_defs_) != 0 || ble_gatts_add_svcs(svc_defs_) != 0) {
        ESP_LOGE(TAG, "registro do servico GATT falhou");
        return false;
    }

    nimble_port_freertos_init(&EspNimbleServer::host_task);
    started_ = true;
    return true;
}

} // namespace cafey::core

#endif // ESP_PLATFORM
