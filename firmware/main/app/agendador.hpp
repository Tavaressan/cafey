#pragma once

#include <cstdint>

#include "core/active_object.hpp"

namespace cafey::app {

/**
 * @brief Active Object do Agendador: relogio (NTP), lista de agendamentos e
 * disparo por horario. Esqueleto nesta fase (FW-02).
 */
class Agendador : public cafey::core::ActiveObject {
public:
    enum class State : uint8_t { Unsynced, Synced };

    Agendador();

    [[nodiscard]] State state() const noexcept { return state_; }

protected:
    void dispatch(const cafey::core::Message& msg) override;

private:
    State state_ = State::Unsynced;
};

} // namespace cafey::app
