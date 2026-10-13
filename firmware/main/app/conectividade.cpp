#include "app/conectividade.hpp"

#include "app/downlink_subscriber.hpp"
#include "app/uplink_publisher.hpp"

namespace cafey::app {

using cafey::core::Message;
using cafey::core::MessageType;

namespace {
// Valores de `estado` que o app reconhece (DeviceModels.kt) e a spec exemplifica.
constexpr const char* kEstadoPreparando = "{\"estado\":\"PREPARANDO\"}";
constexpr const char* kEstadoDesligado = "{\"estado\":\"DESLIGADO\"}";
constexpr const char* kSaudeOnline = "{\"online\":true}";
} // namespace

Conectividade::Conectividade() : ActiveObject("conectividade", 16) {}

void Conectividade::attach_mqtt(cafey::core::IMqttClient* client,
                                const cafey::core::MqttTopics* topics,
                                std::function<bool()> start_mqtt) {
    mqtt_ = client;
    topics_ = topics;
    start_mqtt_ = std::move(start_mqtt);
}

void Conectividade::dispatch(const Message& msg) {
    switch (msg.type) {
        case MessageType::WifiConnected:
            if (mqtt_ == nullptr) {
                state_ = State::Online;
                break;
            }
            state_ = mqtt_->is_connected() ? State::Online : State::Connecting;
            if (!mqtt_started_ && start_mqtt_) {
                mqtt_started_ = start_mqtt_();
            }
            break;
        case MessageType::WifiDisconnected:
            state_ = State::Offline;
            break;
        case MessageType::MqttConnected:
            on_mqtt_connected();
            break;
        case MessageType::MqttDisconnected:
            if (state_ == State::Online) state_ = State::Connecting;
            break;
        case MessageType::EstadoChanged:
            brewing_ = msg.arg != 0;
            publish_estado();
            break;
        default:
            break;
    }
}

void Conectividade::on_mqtt_connected() {
    if (mqtt_ == nullptr || topics_ == nullptr) return;
    state_ = State::Online;

    // Sessao limpa: as assinaturas se perdem a cada reconexao e precisam ser
    // refeitas, senao o `agendamentos` retido nao volta a ser entregue.
    DownlinkSubscriber(*mqtt_, *topics_).subscribe_all();

    // `saude` online faz o backend republicar a lista de agendamentos.
    UplinkPublisher(*mqtt_, *topics_).publish_saude(kSaudeOnline);
    publish_estado();
}

void Conectividade::publish_estado() {
    // Desconectado: o estado corrente sai no proximo MqttConnected.
    if (mqtt_ == nullptr || topics_ == nullptr || !mqtt_->is_connected()) return;
    UplinkPublisher(*mqtt_, *topics_)
        .publish_estado(brewing_ ? kEstadoPreparando : kEstadoDesligado);
}

} // namespace cafey::app
