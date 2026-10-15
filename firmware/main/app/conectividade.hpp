#pragma once

#include <cstdint>
#include <functional>

#include "core/active_object.hpp"
#include "core/mqtt_client.hpp"
#include "core/mqtt_topics.hpp"
#include "storage/event_queue_store.hpp"

namespace cafey::app {

/**
 * @brief Active Object da Conectividade: Wi-Fi, MQTT/TLS, drenagem de eventos
 * pendentes e publicacao dos topicos ascendentes (FW-21/FW-22, spec §5.2 / §6.2).
 *
 * Sem MQTT anexado (dispositivo nao provisionado), segue so o estado do Wi-Fi.
 * Com MQTT anexado, inicia a conexao no primeiro IP e, a cada CONNECT, reassina
 * os topicos descendentes, publica `saude`, o `estado` corrente e drena a fila de eventos.
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

    /**
     * @brief Anexa a fila persistida para drenar eventos pendentes no CONNECT
     * e apos cada novo evento enfileirado (FW-22).
     */
    void attach_event_queue(cafey::storage::EventQueueStore* queue) {
        event_queue_ = queue;
    }

protected:
    void dispatch(const cafey::core::Message& msg) override;

private:
    void on_mqtt_connected();
    void publish_estado();
    void drain_events();

    State state_ = State::Offline;
    cafey::core::IMqttClient* mqtt_ = nullptr;
    const cafey::core::MqttTopics* topics_ = nullptr;
    cafey::storage::EventQueueStore* event_queue_ = nullptr;
    std::function<bool()> start_mqtt_;
    bool mqtt_started_ = false;
    bool brewing_ = false; // ultimo estado informado pela Cafeteira
};

} // namespace cafey::app
