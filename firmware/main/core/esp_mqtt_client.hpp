#pragma once

#if defined(ESP_PLATFORM)

#include "esp_err.h"
#include "mqtt_client.h"

#include "core/mqtt_client.hpp"

namespace cafey::core {

/**
 * @brief Implementacao de IMqttClient sobre o componente esp-mqtt do ESP-IDF.
 *
 * Conecta ao AWS IoT Core por MQTT sobre TLS com certificado X.509 do
 * dispositivo (FW-10, spec §6.1). QoS 1 em publish/subscribe. So compilado para
 * o alvo ESP32 real; a logica que depende dela e coberta por testes de host via
 * IMqttClient.
 */
class EspMqttClient : public IMqttClient {
public:
    EspMqttClient() = default;
    ~EspMqttClient() override;

    EspMqttClient(const EspMqttClient&) = delete;
    EspMqttClient& operator=(const EspMqttClient&) = delete;

    /**
     * @brief Inicializa o cliente esp-mqtt com os certificados e o Last Will de
     * `config` e inicia a conexao (assincrona).
     * @return ESP_OK se o cliente iniciou, ou codigo de erro do ESP-IDF.
     */
    esp_err_t start(const MqttConfig& config);

    [[nodiscard]] bool is_connected() const override { return connected_; }

    bool publish(const std::string& topic, const std::string& payload,
                 int qos, bool retain) override;

    bool subscribe(const std::string& topic, int qos) override;

private:
    static void event_handler(void* arg, esp_event_base_t base,
                              int32_t event_id, void* event_data);

    esp_mqtt_client_handle_t client_ = nullptr;
    bool connected_ = false;
};

} // namespace cafey::core

#endif // ESP_PLATFORM
