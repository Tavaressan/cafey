#include "app/conectividade.hpp"

namespace cafey::app {

using cafey::core::Message;
using cafey::core::MessageType;

Conectividade::Conectividade() : ActiveObject("conectividade", 16) {}

void Conectividade::dispatch(const Message& msg) {
    switch (msg.type) {
        case MessageType::WifiConnected:
            state_ = State::Online;
            break;
        case MessageType::WifiDisconnected:
            state_ = State::Offline;
            break;
        default:
            break;
    }
}

} // namespace cafey::app
