#include "app/conectividade.hpp"

#include <cstdio>
#include <string>

#include "app/downlink_subscriber.hpp"
#include "app/pending_event_publisher.hpp"
#include "app/uplink_publisher.hpp"

namespace cafey::app {

using cafey::core::Message;
using cafey::core::MessageType;

namespace {
// Valores de `estado` que o app reconhece (DeviceModels.kt) e a spec exemplifica.
constexpr const char* kEstadoPreparando = "{\"estado\":\"PREPARANDO\"}";
constexpr const char* kEstadoDesligado = "{\"estado\":\"DESLIGADO\"}";

// `saude` online, com temperatura (C, uma casa) e umidade (%) quando o DHT11 tem
// leitura valida. O backend ignora campos ausentes, entao o payload sem
// ambiente continua valido (sensor solto, firmware sem sensor).
std::string build_saude_online(bool ambiente_valido, int32_t temperatura_dc, int32_t umidade_pct) {
    std::string out = "{\"online\":true";
    if (ambiente_valido) {
        const int32_t abs_dc = temperatura_dc < 0 ? -temperatura_dc : temperatura_dc;
        char buf[64];
        std::snprintf(buf, sizeof(buf), ",\"temperaturaC\":%s%d.%d,\"umidade\":%d",
                      temperatura_dc < 0 ? "-" : "", static_cast<int>(abs_dc / 10),
                      static_cast<int>(abs_dc % 10), static_cast<int>(umidade_pct));
        out += buf;
    }
    out += "}";
    return out;
}
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
        case MessageType::EventQueued:
            drain_events();
            break;
        case MessageType::AmbienteLido:
            ambiente_valido_ = true;
            temperatura_dc_ = msg.arg;
            umidade_pct_ = msg.arg2;
            publish_saude();
            break;
        case MessageType::AmbienteIndisponivel:
            ambiente_valido_ = false;
            publish_saude();
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
    publish_saude();
    publish_estado();
    drain_events();
}

void Conectividade::publish_saude() {
    // Desconectado: a saude sai no proximo MqttConnected, ja com a leitura mais recente.
    if (mqtt_ == nullptr || topics_ == nullptr || !mqtt_->is_connected()) return;
    UplinkPublisher(*mqtt_, *topics_)
        .publish_saude(build_saude_online(ambiente_valido_, temperatura_dc_, umidade_pct_));
}

void Conectividade::publish_estado() {
    // Desconectado: o estado corrente sai no proximo MqttConnected.
    if (mqtt_ == nullptr || topics_ == nullptr || !mqtt_->is_connected()) return;
    UplinkPublisher(*mqtt_, *topics_)
        .publish_estado(brewing_ ? kEstadoPreparando : kEstadoDesligado);
}

void Conectividade::drain_events() {
    if (mqtt_ == nullptr || topics_ == nullptr || event_queue_ == nullptr) return;
    if (!mqtt_->is_connected()) return;
    PendingEventPublisher(*event_queue_, *mqtt_, topics_->eventos).drain();
}

} // namespace cafey::app
