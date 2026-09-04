#pragma once

#include <cstdint>

#include "core/active_object.hpp"

namespace cafey::app {

/**
 * @brief Active Object da Conectividade: Wi-Fi, MQTT/TLS, BLE e fila de eventos
 * pendentes. Esqueleto nesta fase (FW-02) — apenas a maquina de estados basica.
 */
class Conectividade : public cafey::core::ActiveObject {
public:
    enum class State : uint8_t { Offline, Connecting, Online };

    Conectividade();

    [[nodiscard]] State state() const noexcept { return state_; }

protected:
    void dispatch(const cafey::core::Message& msg) override;

private:
    State state_ = State::Offline;
};

} // namespace cafey::app
