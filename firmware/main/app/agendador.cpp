#include "app/agendador.hpp"

namespace cafey::app {

using cafey::core::Message;
using cafey::core::MessageType;

// Verifica os agendamentos a cada segundo; a deduplicacao por minuto abaixo
// garante no maximo um disparo por agendamento a cada minuto de relogio.
Agendador::Agendador(cafey::core::Clock& clock, std::function<void()> on_fire)
    : ActiveObject("agendador", 16, 4096, 5, /*tick_period_ms=*/1000),
      clock_(clock),
      on_fire_(std::move(on_fire)) {}

cafey::core::Clock& Agendador::default_clock() {
    static cafey::core::SystemClock instance;
    return instance;
}

void Agendador::set_schedules(const cafey::storage::Schedule* schedules, size_t count) {
    if (count > kMaxSchedules) count = kMaxSchedules; // fronteira: dados vindos do store
    for (size_t i = 0; i < count; ++i) schedules_[i] = schedules[i];
    count_ = count;
}

void Agendador::dispatch(const Message& msg) {
    switch (msg.type) {
        case MessageType::TimeSynced:
            state_ = State::Synced;
            break;
        default:
            break;
    }
}

void Agendador::on_tick() {
    if (state_ != State::Synced) return; // sem NTP, o relogio interno nao e confiavel

    const std::time_t t = clock_.now();
    const std::time_t minute = t / 60;
    if (minute == last_checked_minute_) return; // ja avaliado neste minuto
    last_checked_minute_ = minute;

    std::tm tm_now{};
    gmtime_r(&t, &tm_now); // NTP entrega UTC; conversao de fuso e responsabilidade do backend
    const uint8_t dow_bit = static_cast<uint8_t>(1u << tm_now.tm_wday); // bit0=domingo

    for (size_t i = 0; i < count_; ++i) {
        const cafey::storage::Schedule& s = schedules_[i];
        if (!s.active) continue;
        if ((s.days_of_week & dow_bit) == 0) continue;
        if (s.hour != tm_now.tm_hour || s.minute != tm_now.tm_min) continue;

        ++fired_count_;
        if (on_fire_) on_fire_();
    }
}

} // namespace cafey::app
