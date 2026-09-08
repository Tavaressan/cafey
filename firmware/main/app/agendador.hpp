#pragma once

#include <ctime>
#include <cstddef>
#include <cstdint>
#include <functional>

#include "core/active_object.hpp"
#include "core/clock.hpp"
#include "storage/schedule.hpp"

namespace cafey::app {

/**
 * @brief Active Object do Agendador (spec §5.2 / §5.5): relogio sincronizado
 * por NTP, lista de agendamentos e disparo do preparo pelo relogio interno,
 * sem depender da nuvem no momento do disparo.
 */
class Agendador : public cafey::core::ActiveObject {
public:
    enum class State : uint8_t { Unsynced, Synced };

    static constexpr size_t kMaxSchedules = 16;

    /**
     * @param clock   fonte de tempo (injetavel para testes de host).
     * @param on_fire callback chamado quando um agendamento dispara (ex.: postar
     *               StartBrew para a Cafeteira).
     */
    explicit Agendador(cafey::core::Clock& clock = default_clock(),
                       std::function<void()> on_fire = nullptr);

    [[nodiscard]] State state() const noexcept { return state_; }
    [[nodiscard]] size_t schedule_count() const noexcept { return count_; }
    [[nodiscard]] int fired_count() const noexcept { return fired_count_; }

    /** Substitui a lista completa de agendamentos (idempotente, spec §6.3). */
    void set_schedules(const cafey::storage::Schedule* schedules, size_t count);

    /** Define o callback de disparo apos a construcao (AO com storage estatico). */
    void set_on_fire(std::function<void()> on_fire) { on_fire_ = std::move(on_fire); }

    static cafey::core::Clock& default_clock();

protected:
    void dispatch(const cafey::core::Message& msg) override;
    void on_tick() override;

private:
    cafey::core::Clock& clock_;
    std::function<void()> on_fire_;
    cafey::storage::Schedule schedules_[kMaxSchedules];
    size_t count_ = 0;
    State state_ = State::Unsynced;
    std::time_t last_checked_minute_ = -1;
    int fired_count_ = 0;
};

} // namespace cafey::app
