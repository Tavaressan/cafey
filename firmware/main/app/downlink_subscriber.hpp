#pragma once

#include "core/mqtt_client.hpp"
#include "core/mqtt_topics.hpp"

namespace cafey::app {

/**
 * @brief Assina os topicos descendentes `comando` e `agendamentos` pelo nome
 * EXATO, em QoS 1 (FW-11, spec-backend §6.3).
 *
 * O AWS IoT Core nao entrega mensagem retida a quem assina com wildcard: o
 * filtro precisa casar exatamente com o topico. Como `agendamentos` e publicado
 * com retain, assinar `dispositivos/+/#` ou similar faria o dispositivo perder a
 * lista vigente ao reconectar. Por isso as assinaturas usam o topico completo.
 */
class DownlinkSubscriber {
public:
    DownlinkSubscriber(cafey::core::IMqttClient& client,
                       const cafey::core::MqttTopics& topics)
        : client_(client), topics_(topics) {}

    /**
     * @brief Assina `comando` e `agendamentos`. Retorna true somente se ambas as
     * assinaturas foram aceitas.
     */
    bool subscribe_all() {
        bool ok = client_.subscribe(topics_.comando, cafey::core::kMqttQos1);
        ok = client_.subscribe(topics_.agendamentos, cafey::core::kMqttQos1) && ok;
        return ok;
    }

private:
    cafey::core::IMqttClient& client_;
    const cafey::core::MqttTopics& topics_;
};

} // namespace cafey::app
