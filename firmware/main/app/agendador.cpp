#include "app/agendador.hpp"

namespace cafey::app {

using cafey::core::Message;
using cafey::core::MessageType;

Agendador::Agendador() : ActiveObject("agendador", 16) {}

void Agendador::dispatch(const Message& msg) {
    switch (msg.type) {
        case MessageType::TimeSynced:
            state_ = State::Synced;
            break;
        default:
            break;
    }
}

} // namespace cafey::app
