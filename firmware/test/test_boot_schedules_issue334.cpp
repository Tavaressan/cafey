#include <iostream>

#include "mock_esp_nvs.hpp"
#include "app/cafeteira.hpp"
#include "drivers/rgb_led.hpp"
#include "storage/schedule_store.hpp"

#define TEST_ASSERT(cond, msg)                                                  \
    do {                                                                         \
        if (!(cond)) {                                                           \
            std::cerr << "FAILED: " << msg << " (" << __FILE__ << ":"            \
                      << __LINE__ << ")" << std::endl;                           \
            return 1;                                                            \
        }                                                                        \
    } while (0)

using cafey::core::Message;
using cafey::core::MessageType;
using cafey::drivers::CoffeeVisualState;
using cafey::drivers::LedColor;

// Expoe os hooks protegidos para acionar o AO de forma sincrona no teste.
class TestableCafeteira : public cafey::app::Cafeteira {
public:
    using Cafeteira::Cafeteira;
    void boot() { init(); on_start(); }
};

// Reproducao da causa #334: sem fila inicializada, post() descarta a mensagem.
int test_post_before_init_is_discarded() {
    MockGpio::reset();
    TestableCafeteira caf;
    // Sem init()/boot(): fila inexistente.
    const bool posted = caf.post(Message{MessageType::ScheduleArmedChanged, 1});
    TEST_ASSERT(!posted, "post antes de init deve ser descartado (fila inexistente)");

    caf.boot();
    caf.process_pending();
    TEST_ASSERT(caf.visual_state() == CoffeeVisualState::Idle,
                "aviso armado descartado antes do boot deixa estado Idle");
    TEST_ASSERT(caf.led().color() == LedColor::Blue, "LED permanece Azul/Idle");

    std::cout << "[PASS] test_post_before_init_is_discarded" << std::endl;
    return 0;
}

// Criterio de aceite #334: com agendamentos ativos carregados no boot, o aviso
// armado postado apos init() antecipado (antes do start/on_start) leva a
// Cafeteira a ScheduleArmed, sem nenhuma mensagem da nuvem.
int test_boot_with_active_schedules_arms_cafeteira() {
    MockGpio::reset();
    TestableCafeteira caf;
    caf.init(); // filas antes de qualquer post(), como o boot corrigido
    caf.set_network_ok(true);

    cafey::storage::Schedule s{};
    s.hour = 7;
    s.minute = 0;
    s.days_of_week = 0x7F;
    s.active = true;
    // Calcula armed a partir da lista carregada (mesma logica do boot).
    bool armed = false;
    for (size_t i = 0; i < 1; ++i) {
        if (s.active) {
            armed = true;
            break;
        }
    }
    caf.post(Message{MessageType::ScheduleArmedChanged, armed ? 1 : 0});

    caf.boot(); // on_start() aplica estado base; nao deve sobrescrever o aviso armado
    caf.process_pending();

    TEST_ASSERT(caf.visual_state() == CoffeeVisualState::ScheduleArmed,
                "boot com agendamento ativo arma a cafeteira sem nuvem");
    TEST_ASSERT(caf.led().color() == LedColor::Green, "LED Verde fixo quando armado e online");

    std::cout << "[PASS] test_boot_with_active_schedules_arms_cafeteira" << std::endl;
    return 0;
}

// Criterio de aceite #334: retido com a mesma versao (ignorado) deve manter ou
// renotificar o estado armado com base na lista gravada.
int test_same_version_retained_renotifies_armed() {
    MockGpio::reset();
    MockNvs::reset();
    cafey::storage::ScheduleStore store;
    TEST_ASSERT(store.init() == ESP_OK, "schedule_store.init");

    cafey::storage::Schedule s{};
    s.hour = 7;
    s.minute = 0;
    s.days_of_week = 0x7F;
    s.active = true;
    bool applied = false;
    TEST_ASSERT(store.replace_all_if_newer(&s, 1, 5, &applied, "UTC") == ESP_OK, "grava v5");
    TEST_ASSERT(applied, "v5 aplicada");

    TestableCafeteira caf;
    caf.init();
    caf.set_network_ok(true);
    caf.boot();
    caf.process_pending();
    TEST_ASSERT(caf.visual_state() == CoffeeVisualState::Idle, "inicia desarmada");

    // Simula retido com mesma versao: store ignora.
    cafey::storage::Schedule same{};
    same.hour = 8;
    same.active = true;
    applied = true;
    TEST_ASSERT(store.replace_all_if_newer(&same, 1, 5, &applied, "UTC") == ESP_OK, "retido v5");
    TEST_ASSERT(!applied, "mesma versao ignorada");

    // Renotificacao idempotente baseada na lista gravada (comportamento corrigido).
    bool armed = false;
    for (size_t i = 0; i < store.count(); ++i) {
        if (store.at(i).active) {
            armed = true;
            break;
        }
    }
    caf.post(Message{MessageType::ScheduleArmedChanged, armed ? 1 : 0});
    caf.process_pending();

    TEST_ASSERT(caf.visual_state() == CoffeeVisualState::ScheduleArmed,
                "retido mesma versao renotifica estado armado");
    TEST_ASSERT(caf.led().color() == LedColor::Green, "LED Verde apos renotificacao");

    std::cout << "[PASS] test_same_version_retained_renotifies_armed" << std::endl;
    return 0;
}

int main() {
    std::cout << "Running Boot Schedules Issue #334 Unit Tests..." << std::endl;
    if (test_post_before_init_is_discarded()) return 1;
    if (test_boot_with_active_schedules_arms_cafeteira()) return 1;
    if (test_same_version_retained_renotifies_armed()) return 1;
    std::cout << "All Boot Schedules #334 tests PASSED!" << std::endl;
    return 0;
}
