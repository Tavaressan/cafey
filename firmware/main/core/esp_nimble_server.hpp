#pragma once

#if defined(ESP_PLATFORM)

#include <cstdint>
#include <string>
#include <vector>

#include "esp_err.h"
#include "host/ble_uuid.h"

#include "core/ble_server.hpp"

namespace cafey::core {

/**
 * @brief Implementacao de IBleServer sobre a stack NimBLE do ESP-IDF (FW-17,
 * spec §6.4). Serve um unico servico GATT primario com as caracteristicas
 * registradas por add_characteristic() e mantem advertising conectavel continuo.
 *
 * So compilada para o alvo ESP32 real; a logica de comando/estado/agendamentos
 * e do proxy de eventos e coberta por testes de host via IBleServer.
 */
class EspNimbleServer : public IBleServer {
public:
    explicit EspNimbleServer(const std::string& service_uuid);
    ~EspNimbleServer() override;

    EspNimbleServer(const EspNimbleServer&) = delete;
    EspNimbleServer& operator=(const EspNimbleServer&) = delete;

    void add_characteristic(const std::string& uuid, uint8_t props,
                            BleWriteCallback on_write) override;
    bool start(const std::string& device_name) override;
    bool update_value(const std::string& uuid, const std::string& value) override;

private:
    struct Entry {
        ble_uuid128_t uuid;
        uint8_t props;
        BleWriteCallback on_write;
        std::string value;
        uint16_t val_handle = 0;
    };

    static int gap_event(struct ble_gap_event* event, void* arg);
    static int access_cb(uint16_t conn_handle, uint16_t attr_handle,
                         struct ble_gatt_access_ctxt* ctxt, void* arg);
    static void on_sync();
    static void host_task(void* param);

    void advertise();

    static EspNimbleServer* self_;

    ble_uuid128_t service_uuid_{};
    std::vector<Entry> entries_;
    std::string device_name_;
    uint8_t own_addr_type_ = 0;
    bool started_ = false;

    // Estruturas exigidas pela NimBLE vivas enquanto o servidor existir.
    std::vector<struct ble_gatt_chr_def> chr_defs_;
    struct ble_gatt_svc_def svc_defs_[2]{};
};

} // namespace cafey::core

#endif // ESP_PLATFORM
