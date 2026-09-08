#pragma once

#include <string>

namespace cafey::core {

// QoS 1 em todos os topicos (spec §6.1: "QoS: 1 em todos os topicos").
inline constexpr int kMqttQos1 = 1;

/**
 * @brief Parametros de conexao MQTT sobre TLS ao AWS IoT Core (FW-10, spec §6.1).
 *
 * Certificado X.509 e chave privada vem de config/NVS (MqttConfigStore) — nunca
 * embutidos no binario.
 */
struct MqttConfig {
    std::string broker_uri;         // ex.: "mqtts://xxxx-ats.iot.<regiao>.amazonaws.com:8883"
    std::string client_id;          // deviceId
    std::string root_ca_pem;        // CA raiz da Amazon
    std::string device_cert_pem;    // certificado X.509 do dispositivo
    std::string private_key_pem;    // chave privada do dispositivo
    std::string last_will_topic;    // topico de saude usado como Last Will
    std::string last_will_payload;  // ex.: {"online":false}
    bool last_will_retain = true;   // Will retido (spec-backend §6.3)
};

/**
 * @brief Abstracao do cliente MQTT.
 *
 * Isola a logica de topicos (FW-11/FW-12) e de fila de eventos (FW-16) do
 * componente esp-mqtt, permitindo testa-la em host sem broker real.
 */
class IMqttClient {
public:
    virtual ~IMqttClient() = default;

    [[nodiscard]] virtual bool is_connected() const = 0;

    /** @brief Publica em `topic`. Retorna true se a mensagem foi aceita para envio. */
    virtual bool publish(const std::string& topic, const std::string& payload,
                         int qos, bool retain) = 0;

    /** @brief Assina `topic`. Retorna true se a assinatura foi aceita. */
    virtual bool subscribe(const std::string& topic, int qos) = 0;
};

} // namespace cafey::core
