#include <ctime>
#include <functional>
#include <iostream>

#include "app/agendador.hpp"
#include "core/clock.hpp"
#include "storage/schedule.hpp"

#define TEST_ASSERT(cond, msg)                                                  \
    do {                                                                       \
        if (!(cond)) {                                                         \
            std::cerr << "FAILED: " << msg << " (" << __FILE__ << ":"          \
                      << __LINE__ << ")" << std::endl;                         \
            return 1;                                                          \
        }                                                                      \
    } while (0)

using cafey::core::Message;
using cafey::core::MessageType;

// 2024-01-03 07:30:00 UTC (quarta-feira; tm_wday == 3).
static constexpr std::time_t kWed0730Utc = 1704267000;

// Relogio injetavel: controla o "agora" sem depender de tempo real.
class FakeClock : public cafey::core::Clock {
public:
    std::time_t value = 0;
    std::time_t now() const override { return value; }
};

// Expoe on_tick() para acionar o AO de forma sincrona no teste.
class TestableAgendador : public cafey::app::Agendador {
public:
    using Agendador::Agendador;
    void tick() { on_tick(); }
};

int test_fires_when_clock_matches_schedule() {
    FakeClock clock;
    int fired = 0;
    TestableAgendador age(clock, [&](const cafey::storage::Schedule&) { ++fired; });
    age.init();
    age.post(Message{MessageType::TimeSynced, 0});
    age.process_pending();

    cafey::storage::Schedule s{};
    s.hour = 7;
    s.minute = 30;
    s.days_of_week = 0x7F; // todos os dias
    s.active = true;
    age.set_schedules(&s, 1);

    clock.value = kWed0730Utc - 60; // 07:29
    age.tick();
    TEST_ASSERT(fired == 0, "nao dispara antes do horario");

    clock.value = kWed0730Utc; // 07:30
    age.tick();
    TEST_ASSERT(fired == 1, "dispara no horario agendado");

    clock.value = kWed0730Utc + 30; // 07:30:30, mesmo minuto
    age.tick();
    TEST_ASSERT(fired == 1, "nao repete disparo no mesmo minuto");

    std::cout << "[PASS] test_fires_when_clock_matches_schedule" << std::endl;
    return 0;
}

int test_does_not_fire_when_unsynced() {
    FakeClock clock;
    int fired = 0;
    TestableAgendador age(clock, [&](const cafey::storage::Schedule&) { ++fired; });
    age.init();

    cafey::storage::Schedule s{};
    s.hour = 7;
    s.minute = 30;
    s.days_of_week = 0x7F;
    s.active = true;
    age.set_schedules(&s, 1);

    clock.value = kWed0730Utc;
    age.tick();
    TEST_ASSERT(fired == 0, "sem sincronizacao NTP o agendador nao dispara");

    std::cout << "[PASS] test_does_not_fire_when_unsynced" << std::endl;
    return 0;
}

int test_does_not_fire_on_unscheduled_day() {
    FakeClock clock;
    int fired = 0;
    TestableAgendador age(clock, [&](const cafey::storage::Schedule&) { ++fired; });
    age.init();
    age.post(Message{MessageType::TimeSynced, 0});
    age.process_pending();

    cafey::storage::Schedule s{};
    s.hour = 7;
    s.minute = 30;
    s.days_of_week = 0x01; // apenas domingo
    s.active = true;
    age.set_schedules(&s, 1);

    clock.value = kWed0730Utc; // quarta-feira
    age.tick();
    TEST_ASSERT(fired == 0, "nao dispara em dia fora da mascara");

    std::cout << "[PASS] test_does_not_fire_on_unscheduled_day" << std::endl;
    return 0;
}

int test_inactive_schedule_does_not_fire() {
    FakeClock clock;
    int fired = 0;
    TestableAgendador age(clock, [&](const cafey::storage::Schedule&) { ++fired; });
    age.init();
    age.post(Message{MessageType::TimeSynced, 0});
    age.process_pending();

    cafey::storage::Schedule s{};
    s.hour = 7;
    s.minute = 30;
    s.days_of_week = 0x7F;
    s.active = false;
    age.set_schedules(&s, 1);

    clock.value = kWed0730Utc;
    age.tick();
    TEST_ASSERT(fired == 0, "agendamento inativo nao dispara");

    std::cout << "[PASS] test_inactive_schedule_does_not_fire" << std::endl;
    return 0;
}

// #200: o callback recebe o agendamento que disparou, com a duracao dele.
int test_fire_passes_schedule_duration() {
    FakeClock clock;
    uint16_t fired_duration = 0;
    TestableAgendador age(clock, [&](const cafey::storage::Schedule& s) {
        fired_duration = s.duration_s;
    });
    age.init();
    age.post(Message{MessageType::TimeSynced, 0});
    age.process_pending();

    cafey::storage::Schedule s{};
    s.hour = 7;
    s.minute = 30;
    s.days_of_week = 0x7F;
    s.active = true;
    s.duration_s = 480;
    age.set_schedules(&s, 1);

    clock.value = kWed0730Utc;
    age.tick();
    TEST_ASSERT(fired_duration == 480, "on_fire recebe a duracao do agendamento");

    std::cout << "[PASS] test_fire_passes_schedule_duration" << std::endl;
    return 0;
}

// FW-23: com o fuso de Sao Paulo (UTC-3), 07:00 locais dispara as 10:00 UTC.
int test_applies_utc_offset() {
    FakeClock clock;
    int fired = 0;
    TestableAgendador age(clock, [&](const cafey::storage::Schedule&) { ++fired; });
    age.init();
    age.post(Message{MessageType::TimeSynced, 0});
    age.process_pending();
    age.set_utc_offset(-3 * 3600);

    cafey::storage::Schedule s{};
    s.hour = 7;
    s.minute = 0;
    s.days_of_week = 0x7F;
    s.active = true;
    age.set_schedules(&s, 1);

    clock.value = kWed0730Utc - 30 * 60; // 07:00 UTC = 04:00 locais
    age.tick();
    TEST_ASSERT(fired == 0, "nao dispara as 07:00 UTC");

    clock.value = kWed0730Utc + 150 * 60; // 10:00 UTC = 07:00 locais
    age.tick();
    TEST_ASSERT(fired == 1, "dispara as 10:00 UTC");

    std::cout << "[PASS] test_applies_utc_offset" << std::endl;
    return 0;
}

// FW-23: o dia da semana tambem e local. Terca 22:00 em Sao Paulo ja e quarta
// 01:00 UTC; um agendamento so de terca precisa disparar nesse instante.
int test_local_weekday_across_utc_midnight() {
    FakeClock clock;
    int fired = 0;
    TestableAgendador age(clock, [&](const cafey::storage::Schedule&) { ++fired; });
    age.init();
    age.post(Message{MessageType::TimeSynced, 0});
    age.process_pending();
    age.set_utc_offset(-3 * 3600);

    cafey::storage::Schedule s{};
    s.hour = 22;
    s.minute = 0;
    s.days_of_week = 0x04; // apenas terca
    s.active = true;
    age.set_schedules(&s, 1);

    const std::time_t wed0000Utc = kWed0730Utc - (7 * 3600 + 30 * 60);

    clock.value = wed0000Utc - 2 * 3600; // terca 22:00 UTC = terca 19:00 locais
    age.tick();
    TEST_ASSERT(fired == 0, "nao dispara as 22:00 UTC de terca");

    clock.value = wed0000Utc + 3600; // quarta 01:00 UTC = terca 22:00 locais
    age.tick();
    TEST_ASSERT(fired == 1, "dispara na terca 22:00 local (quarta 01:00 UTC)");

    std::cout << "[PASS] test_local_weekday_across_utc_midnight" << std::endl;
    return 0;
}

int main() {
    std::cout << "Running Cafey Agendador (FW-14) Unit Tests..." << std::endl;
    if (test_fires_when_clock_matches_schedule()) return 1;
    if (test_does_not_fire_when_unsynced()) return 1;
    if (test_does_not_fire_on_unscheduled_day()) return 1;
    if (test_inactive_schedule_does_not_fire()) return 1;
    if (test_fire_passes_schedule_duration()) return 1;
    if (test_applies_utc_offset()) return 1;
    if (test_local_weekday_across_utc_midnight()) return 1;
    std::cout << "All Agendador tests PASSED!" << std::endl;
    return 0;
}
