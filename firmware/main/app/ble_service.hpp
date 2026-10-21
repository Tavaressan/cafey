#pragma once

#include <cstddef>
#include <functional>
#include <string>
#include <utility>

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

    // Limite alinhado a Agendador::kMaxSchedules / ScheduleStore::kMaxSchedules.
    static constexpr size_t kMaxSchedules = 16;

    using DeviceInfo = BleDeviceInfo;
    using CommandHandler = std::function<void(const CoffeeCommand&)>;
    using ScheduleHandler =
        std::function<void(const cafey::storage::Schedule*, size_t, const SchedulePayloadMeta&)>;
    using WifiProvisionHandler =
        std::function<void(const std::string& ssid, const std::string& password)>;

    BleService(cafey::core::IBleServer& server, CommandHandler on_command,
               ScheduleHandler on_schedules,
               WifiProvisionHandler on_wifi_provision = nullptr,
               DeviceInfo device_info = DeviceInfo())
        : server_(server),
          on_command_(std::move(on_command)),
          on_schedules_(std::move(on_schedules)),
          on_wifi_provision_(std::move(on_wifi_provision)),
          device_info_(std::move(device_info)) {}

    void set_on_wifi_provision(WifiProvisionHandler handler) {
        on_wifi_provision_ = std::move(handler);
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

        bool ok = server_.start(device_name);
        if (ok) {
            std::string serial = device_info_.serial.empty() ? device_name : device_info_.serial;
            std::string info_json = "{\"serial\":\"" + serial +
                                    "\",\"firmware\":\"" + device_info_.firmware +
                                    "\",\"model\":\"" + device_info_.model + "\"}";
            server_.update_value(kDeviceInfoUuid, info_json);
            publish_wifi_status("DISCONNECTED");
        }
        return ok;
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

private:
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
};

} // namespace cafey::app
