#pragma once

#include <cstddef>
#include <functional>
#include <string>
#include <utility>
#include <vector>

#include "app/coffee_command.hpp"
#include "app/schedule_payload.hpp"
#include "core/ble_server.hpp"
#include "core/mini_json.hpp"
#include "storage/schedule.hpp"

namespace cafey::app {

struct BleDeviceInfo {
    std::string serial;
    std::string firmware = "1.0.0";
    std::string model = "ESP32-WROOM-32";

    BleDeviceInfo() = default;
    BleDeviceInfo(std::string s, std::string fw = "1.0.0", std::string mod = "ESP32-WROOM-32")
        : serial(std::move(s)), firmware(std::move(fw)), model(std::move(mod)) {}
};

/**
 * @brief Servico BLE da cafeteira (FW-17, spec §6.4, UC-04 #249): expoe as caracteristicas
 * de comando (write), estado (read/notify), agendamentos (write),
 * provisionamento Wi-Fi (write), status Wi-Fi (read/notify) e dados do dispositivo (read).
 *
 * A camada BLE apenas traduz bytes <-> chamada: a regra de negocio fica nos
 * handlers de dominio injetados (`on_command`, `on_schedules`, `on_wifi_provision`).
 * A dependencia da stack NimBLE fica atras de cafey::core::IBleServer.
 */
class BleService {
public:
    // UUIDs 128-bit do servico cafey. Base: 6b3dXXXX-0b0b-4a3a-9c0e-cafe1a000000.
    static constexpr const char* kServiceUuid = "6b3d0001-0b0b-4a3a-9c0e-cafe1a000000";
    static constexpr const char* kCommandUuid = "6b3d0002-0b0b-4a3a-9c0e-cafe1a000000";
    static constexpr const char* kStateUuid = "6b3d0003-0b0b-4a3a-9c0e-cafe1a000000";
    static constexpr const char* kScheduleUuid = "6b3d0004-0b0b-4a3a-9c0e-cafe1a000000";
    static constexpr const char* kWifiProvisionUuid = "6b3d0005-0b0b-4a3a-9c0e-cafe1a000000";
    static constexpr const char* kWifiStatusUuid = "6b3d0006-0b0b-4a3a-9c0e-cafe1a000000";
    static constexpr const char* kDeviceInfoUuid = "6b3d0007-0b0b-4a3a-9c0e-cafe1a000000";
    // FW-30 (#332): gerenciamento de redes Wi-Fi (listagem e exclusao). 0008/0009 = eventos.
    static constexpr const char* kWifiNetworksUuid = "6b3d000b-0b0b-4a3a-9c0e-cafe1a000000";
    static constexpr const char* kWifiManageUuid = "6b3d000c-0b0b-4a3a-9c0e-cafe1a000000";

    // Codigo de uso unico da janela de vinculo aberta pelo toque longo (FW-24, #275).
    static constexpr const char* kVinculoUuid = "6b3d000a-0b0b-4a3a-9c0e-cafe1a000000";

    // Limite alinhado a Agendador::kMaxSchedules / ScheduleStore::kMaxSchedules.
    static constexpr size_t kMaxSchedules = 16;

    using DeviceInfo = BleDeviceInfo;
    using CommandHandler = std::function<void(const CoffeeCommand&)>;
    using ScheduleHandler =
        std::function<void(const cafey::storage::Schedule*, size_t, const SchedulePayloadMeta&)>;
    using WifiProvisionHandler =
        std::function<void(const std::string& ssid, const std::string& password)>;
    using WifiForgetHandler = std::function<void(const std::string& ssid)>;
    using WifiForgetAllHandler = std::function<void()>;
    // Retorna JSON com rede atual e redes salvas (sem senhas).
    using WifiNetworksProvider = std::function<std::string()>;
    // FW-24/#275: comandos BLE destrutivos exigem janela de vinculo aberta. Sem
    // FW-24 no branch, o padrao conserva a seguranca e nega a operacao.
    using DestructiveBleGuard = std::function<bool()>;

    BleService(cafey::core::IBleServer& server, CommandHandler on_command,
               ScheduleHandler on_schedules,
               WifiProvisionHandler on_wifi_provision = nullptr,
               DeviceInfo device_info = DeviceInfo())
        : server_(server),
          on_command_(std::move(on_command)),
          on_schedules_(std::move(on_schedules)),
          on_wifi_provision_(std::move(on_wifi_provision)),
          device_info_(std::move(device_info)),
          destructive_ble_guard_([] { return false; }) {}

    void set_on_wifi_provision(WifiProvisionHandler handler) {
        on_wifi_provision_ = std::move(handler);
    }

    void set_wifi_management(WifiForgetHandler on_forget, WifiForgetAllHandler on_forget_all,
                             WifiNetworksProvider networks_provider) {
        on_wifi_forget_ = std::move(on_forget);
        on_wifi_forget_all_ = std::move(on_forget_all);
        wifi_networks_provider_ = std::move(networks_provider);
    }

    /** @brief Define a prova de posse para comandos BLE destrutivos (FW-24). */
    void set_destructive_ble_guard(DestructiveBleGuard guard) {
        destructive_ble_guard_ = std::move(guard);
    }

    void set_device_info(const DeviceInfo& info) {
        device_info_ = info;
    }

    /** @brief Registra as caracteristicas e inicia o advertising. */
    bool begin(const std::string& device_name) {
        server_.add_characteristic(
            kCommandUuid, cafey::core::kBleWrite,
            [this](const std::string& value) { handle_command_write(value); });
        server_.add_characteristic(kStateUuid,
                                   cafey::core::kBleRead | cafey::core::kBleNotify, nullptr);
        server_.add_characteristic(
            kScheduleUuid, cafey::core::kBleWrite,
            [this](const std::string& value) { handle_schedule_write(value); });
        server_.add_characteristic(
            kWifiProvisionUuid, cafey::core::kBleWrite,
            [this](const std::string& value) { handle_wifi_provision_write(value); });
        server_.add_characteristic(
            kWifiStatusUuid, cafey::core::kBleRead | cafey::core::kBleNotify, nullptr);
        server_.add_characteristic(
            kDeviceInfoUuid, cafey::core::kBleRead, nullptr);
        // FW-30: redes salvas (leitura, sem senhas) e gerenciamento (escrita).
        server_.add_characteristic(
            kWifiNetworksUuid, cafey::core::kBleRead | cafey::core::kBleNotify, nullptr);
        server_.add_characteristic(
            kWifiManageUuid, cafey::core::kBleWrite,
            [this](const std::string& value) { handle_wifi_manage_write(value); });
        server_.add_characteristic(kVinculoUuid, cafey::core::kBleRead, nullptr);

        bool ok = server_.start(device_name);
        if (ok) {
            std::string serial = device_info_.serial.empty() ? device_name : device_info_.serial;
            std::string info_json = "{\"serial\":\"" + serial +
                                    "\",\"firmware\":\"" + device_info_.firmware +
                                    "\",\"model\":\"" + device_info_.model + "\"}";
            server_.update_value(kDeviceInfoUuid, info_json);
            server_.update_value(kVinculoUuid, "{\"codigo\":null}");
            publish_wifi_status("DISCONNECTED");
            publish_wifi_networks();
        }
        return ok;
    }

    /**
     * @brief Publica o codigo da janela de vinculo (FW-24, #275) na
     * caracteristica de leitura. String vazia limpa a caracteristica.
     */
    bool publish_vinculo(const std::string& codigo) {
        const std::string json = codigo.empty()
                                     ? "{\"codigo\":null}"
                                     : "{\"codigo\":\"" + codigo + "\"}";
        return server_.update_value(kVinculoUuid, json);
    }

    /**
     * @brief Atualiza a caracteristica de estado (read/notify) com o mesmo corpo
     * publicado no topico `dispositivos/{id}/estado`.
     */
    bool publish_state(const std::string& state_json) {
        return server_.update_value(kStateUuid, state_json);
    }

    /**
     * @brief Notifica status Wi-Fi via caracteristica kWifiStatusUuid.
     */
    bool publish_wifi_status(const std::string& state, const std::string& ip = "",
                             const std::string& error = "") {
        std::string json = "{\"state\":\"" + state + "\",\"ip\":";
        if (ip.empty()) {
            json += "null";
        } else {
            json += "\"" + ip + "\"";
        }
        json += ",\"error\":";
        if (error.empty()) {
            json += "null";
        } else {
            json += "\"" + error + "\"";
        }
        json += "}";
        return server_.update_value(kWifiStatusUuid, json);
    }

    /** @brief Publica a lista de redes salvas e a rede atual (sem senhas). */
    bool publish_wifi_networks() {
        if (wifi_networks_provider_) {
            return server_.update_value(kWifiNetworksUuid, wifi_networks_provider_());
        }
        return server_.update_value(kWifiNetworksUuid, R"({"ssid_atual":null,"redes":[]})");
    }

private:
    void handle_wifi_manage_write(const std::string& value) {
        std::string acao;
        if (!cafey::core::mini_json::get_string(value, "acao", acao)) {
            publish_wifi_status("INVALID_PAYLOAD", "", "acao ausente");
            return;
        }

        if (acao == "ESQUECER_REDE" || acao == "ESQUECER_TODAS_REDES") {
            // Ponto de verificacao isolado (FW-24/#275): destrutivo por BLE exige
            // janela de vinculo aberta. Sem FW-24 no branch, o guard padrao nega.
            if (!destructive_ble_guard_ || !destructive_ble_guard_()) {
                publish_wifi_status("INVALID_PAYLOAD", "",
                                    "janela de vinculo fechada (toque longo 5s)");
                return;
            }
        }

        if (acao == "ESQUECER_REDE") {
            std::string ssid;
            if (!cafey::core::mini_json::get_string(value, "ssid", ssid) || ssid.empty()) {
                publish_wifi_status("INVALID_PAYLOAD", "", "ssid ausente");
                return;
            }
            if (on_wifi_forget_) on_wifi_forget_(ssid);
            publish_wifi_networks();
        } else if (acao == "ESQUECER_TODAS_REDES") {
            if (on_wifi_forget_all_) on_wifi_forget_all_();
            publish_wifi_networks();
        } else {
            publish_wifi_status("INVALID_PAYLOAD", "", "acao desconhecida");
        }
    }

    void handle_command_write(const std::string& value) {
        const CoffeeCommand cmd = parse_coffee_command(value);
        if (cmd.action != CoffeeAction::Unknown && on_command_) {
            on_command_(cmd);
        }
    }

    void handle_schedule_write(const std::string& value) {
        cafey::storage::Schedule list[kMaxSchedules];
        size_t count = 0;
        SchedulePayloadMeta meta{};
        if (parse_schedule_payload(value, list, kMaxSchedules, &count, &meta) && on_schedules_) {
            on_schedules_(list, count, meta);
        }
    }

    void handle_wifi_provision_write(const std::string& value) {
        std::string ssid;
        std::string password;
        if (!cafey::core::mini_json::get_string(value, "ssid", ssid) ||
            ssid.empty() || ssid.size() > 32) {
            publish_wifi_status("INVALID_PAYLOAD", "", "Invalid SSID");
            return;
        }
        if (!cafey::core::mini_json::get_string(value, "password", password) ||
            password.size() > 64) {
            publish_wifi_status("INVALID_PAYLOAD", "", "Invalid password");
            return;
        }

        publish_wifi_status("CONNECTING");

        if (on_wifi_provision_) {
            on_wifi_provision_(ssid, password);
        }
    }

    cafey::core::IBleServer& server_;
    CommandHandler on_command_;
    ScheduleHandler on_schedules_;
    WifiProvisionHandler on_wifi_provision_;
    DeviceInfo device_info_;
    WifiForgetHandler on_wifi_forget_;
    WifiForgetAllHandler on_wifi_forget_all_;
    WifiNetworksProvider wifi_networks_provider_;
    DestructiveBleGuard destructive_ble_guard_;
};

} // namespace cafey::app
