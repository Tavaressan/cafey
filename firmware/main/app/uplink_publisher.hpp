#pragma once

#include <string>

#include "core/mqtt_client.hpp"
#include "core/mqtt_topics.hpp"

namespace cafey::app {

/**
 * @brief Publica os topicos ascendentes com a politica de retain de cada um
 * (FW-12, spec §6.2):
 *  - `estado`  -> QoS 1, retain (ultimo estado sobrevive no broker)
 *  - `eventos` -> QoS 1, sem retain (publicacao unica, vai para o historico)
 *  - `saude`   -> QoS 1, sem retain nas publicacoes periodicas
 *
 * O Last Will retido de `saude` ({"online":false}) e configurado na conexao
 * (MqttConfig::last_will_*), nao aqui: ver make_last_will().
 */
class UplinkPublisher {
public:
    UplinkPublisher(cafey::core::IMqttClient& client,
                    const cafey::core::MqttTopics& topics)
        : client_(client), topics_(topics) {}

    bool publish_estado(const std::string& json_payload) {
        return client_.publish(topics_.estado, json_payload, cafey::core::kMqttQos1,
                               /*retain=*/true);
    }

    bool publish_evento(const std::string& json_payload) {
        return client_.publish(topics_.eventos, json_payload, cafey::core::kMqttQos1,
                               /*retain=*/false);
    }

    bool publish_saude(const std::string& json_payload) {
        return client_.publish(topics_.saude, json_payload, cafey::core::kMqttQos1,
                               /*retain=*/false);
    }

    /**
     * @brief Preenche os campos de Last Will de `config` para `saude`: payload
     * {"online":false} publicado com retain quando o broker detecta a queda
     * (spec-backend §6.3).
     */
    static void make_last_will(const cafey::core::MqttTopics& topics,
                               cafey::core::MqttConfig& config) {
        config.last_will_topic = topics.saude;
        config.last_will_payload = "{\"online\":false}";
        config.last_will_retain = true;
    }

private:
    cafey::core::IMqttClient& client_;
    const cafey::core::MqttTopics& topics_;
};

} // namespace cafey::app
