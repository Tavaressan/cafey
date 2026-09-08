#pragma once

#include <string>

namespace cafey::core {

/**
 * @brief Monta os topicos MQTT a partir do deviceId. Raiz:
 * `dispositivos/{deviceId}/` (spec §6.2).
 *
 * Os nomes sao EXATOS, sem wildcard: no AWS IoT Core a mensagem retida nao e
 * entregue a quem assina com '+'/'#' (spec-backend §6.3), entao o dispositivo
 * precisa assinar o proprio topico completo para receber `agendamentos` retido.
 */
struct MqttTopics {
    explicit MqttTopics(const std::string& device_id)
        : comando("dispositivos/" + device_id + "/comando"),
          agendamentos("dispositivos/" + device_id + "/agendamentos"),
          estado("dispositivos/" + device_id + "/estado"),
          eventos("dispositivos/" + device_id + "/eventos"),
          saude("dispositivos/" + device_id + "/saude") {}

    std::string comando;
    std::string agendamentos;
    std::string estado;
    std::string eventos;
    std::string saude;
};

} // namespace cafey::core
