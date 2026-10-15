#pragma once

#if defined(ESP_PLATFORM)

#include <functional>
#include <string>

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
    using MessageHandler = std::function<void(const std::string& topic, const std::string& payload)>;

    EspMqttClient() = default;
    ~EspMqttClient() override;

    EspMqttClient(const EspMqttClient&) = delete;
    EspMqttClient& operator=(const EspMqttClient&) = delete;

    /**
     * @brief Inicializa o cliente esp-mqtt com os certificados e o Last Will de
     * `config` e inicia a conexao (assincrona). Guarda uma copia de `config`: o
     * esp-mqtt nao copia o CA, entao as strings precisam viver com o cliente.
     * @return ESP_OK se o cliente iniciou, ou codigo de erro do ESP-IDF.
     */
    esp_err_t start(const MqttConfig& config);

    /**
     * @brief Callbacks (FW-21), chamados na task do esp-mqtt. Os de conexao devem
     * so postar para um Active Object. Definir antes de start().
     */
    void set_on_connected(std::function<void()> cb) { on_connected_ = std::move(cb); }
    void set_on_disconnected(std::function<void()> cb) { on_disconnected_ = std::move(cb); }
    void set_on_message(MessageHandler cb) { on_message_ = std::move(cb); }

    [[nodiscard]] bool is_connected() const override { return connected_; }

    bool publish(const std::string& topic, const std::string& payload,
                 int qos, bool retain) override;

    bool subscribe(const std::string& topic, int qos) override;

private:
    static void event_handler(void* arg, esp_event_base_t base,
                              int32_t event_id, void* event_data);

    esp_mqtt_client_handle_t client_ = nullptr;
    MqttConfig config_;
    volatile bool connected_ = false;
    std::function<void()> on_connected_;
    std::function<void()> on_disconnected_;
    MessageHandler on_message_;
};

} // namespace cafey::core

#endif // ESP_PLATFORM
