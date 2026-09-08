#pragma once

// Fake host-side de cafey::core::IMqttClient: registra publicacoes e assinaturas
// em memoria para testar a logica de topicos (FW-11/FW-12) e da fila de eventos
// (FW-16) sem broker real.

#include <string>
#include <vector>

#include "core/mqtt_client.hpp"

struct RecordedPublish {
    std::string topic;
    std::string payload;
    int qos;
    bool retain;
};

struct RecordedSubscribe {
    std::string topic;
    int qos;
};

class FakeMqttClient : public cafey::core::IMqttClient {
public:
    bool connected = true;
    bool publish_result = true; // valor retornado por publish()
    bool subscribe_result = true;
    int fail_publish_after = -1; // se >= 0, publish() falha a partir dessa chamada

    std::vector<RecordedPublish> publishes;
    std::vector<RecordedSubscribe> subscribes;

    [[nodiscard]] bool is_connected() const override { return connected; }

    bool publish(const std::string& topic, const std::string& payload, int qos,
                 bool retain) override {
        if (fail_publish_after >= 0 &&
            static_cast<int>(publishes.size()) >= fail_publish_after) {
            return false;
        }
        publishes.push_back({topic, payload, qos, retain});
        return publish_result;
    }

    bool subscribe(const std::string& topic, int qos) override {
        subscribes.push_back({topic, qos});
        return subscribe_result;
    }
};
