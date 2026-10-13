#pragma once

#include <cstdint>
#include <functional>

#include "core/active_object.hpp"
#include "core/mqtt_client.hpp"
#include "core/mqtt_topics.hpp"

namespace cafey::app {

/**
 * @brief Active Object da Conectividade: Wi-Fi, MQTT/TLS e publicacao dos
 * topicos ascendentes (FW-21, spec §5.2 / §6.2).
 *
 * Sem MQTT anexado (dispositivo nao provisionado), segue so o estado do Wi-Fi.
 * Com MQTT anexado, inicia a conexao no primeiro IP e, a cada CONNECT, reassina
 * os topicos descendentes e publica `saude` e o `estado` corrente.
 */
class Conectividade : public cafey::core::ActiveObject {
public:
    enum class State : uint8_t { Offline, Connecting, Online };

    Conectividade();

    [[nodiscard]] State state() const noexcept { return state_; }

    /**
     * @brief Anexa o cliente MQTT. `start_mqtt` inicia a conexao e e chamado uma
     * unica vez, no primeiro WifiConnected: dai em diante o esp-mqtt reconecta
     * sozinho. Chamar antes de start().
     */
    void attach_mqtt(cafey::core::IMqttClient* client, const cafey::core::MqttTopics* topics,
                     std::function<bool()> start_mqtt);

protected:
    void dispatch(const cafey::core::Message& msg) override;

private:
    void on_mqtt_connected();
    void publish_estado();

    State state_ = State::Offline;
    cafey::core::IMqttClient* mqtt_ = nullptr;
    const cafey::core::MqttTopics* topics_ = nullptr;
    std::function<bool()> start_mqtt_;
    bool mqtt_started_ = false;
    bool brewing_ = false; // ultimo estado informado pela Cafeteira
};

} // namespace cafey::app
