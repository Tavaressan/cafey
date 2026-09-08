#pragma once

// Fake host-side de cafey::core::IBleServer: registra caracteristicas e valores
// em memoria e permite simular escritas de um cliente BLE, para testar a logica
// de comando/estado/agendamentos (FW-17) e do proxy de eventos (FW-18) sem
// stack NimBLE real. Espelha test/mock_mqtt_client.hpp.

#include <map>
#include <string>
#include <utility>
#include <vector>

#include "core/ble_server.hpp"

class FakeBleServer : public cafey::core::IBleServer {
public:
    struct Characteristic {
        uint8_t props = 0;
        cafey::core::BleWriteCallback on_write;
        std::string value;
    };

    bool started = false;
    std::string device_name;
    std::map<std::string, Characteristic> characteristics;
    // Historico de notificacoes na ordem, como "uuid=value".
    std::vector<std::pair<std::string, std::string>> notifications;

    void add_characteristic(const std::string& uuid, uint8_t props,
                            cafey::core::BleWriteCallback on_write) override {
        characteristics[uuid] = Characteristic{props, std::move(on_write), ""};
    }

    bool start(const std::string& name) override {
        started = true;
        device_name = name;
        return true;
    }

    bool update_value(const std::string& uuid, const std::string& value) override {
        auto it = characteristics.find(uuid);
        if (it == characteristics.end()) return false;
        it->second.value = value;
        notifications.emplace_back(uuid, value);
        return true;
    }

    // --- Helpers de teste ---

    // Simula um cliente BLE escrevendo `value` na caracteristica `uuid`.
    void client_write(const std::string& uuid, const std::string& value) {
        auto it = characteristics.find(uuid);
        if (it != characteristics.end() && it->second.on_write) {
            it->second.on_write(value);
        }
    }

    // Valor legivel atual da caracteristica (o que um cliente leria).
    std::string read(const std::string& uuid) const {
        auto it = characteristics.find(uuid);
        return it == characteristics.end() ? std::string() : it->second.value;
    }

    bool has(const std::string& uuid) const {
        return characteristics.find(uuid) != characteristics.end();
    }

    uint8_t props(const std::string& uuid) const {
        auto it = characteristics.find(uuid);
        return it == characteristics.end() ? 0 : it->second.props;
    }
};
