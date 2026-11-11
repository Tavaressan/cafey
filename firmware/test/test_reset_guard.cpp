#include <iostream>

#include "mock_esp_nvs.hpp"
#include "mock_esp_task_wdt.hpp"
#include "core/reset_guard.hpp"
#include "app/cafeteira.hpp"

#define TEST_ASSERT(cond, msg)                                                  \
    do {                                                                        \
        if (!(cond)) {                                                          \
            std::cerr << "FAILED: " << msg << " (" << __FILE__ << ":"           \
                      << __LINE__ << ")" << std::endl;                          \
            return 1;                                                           \
        }                                                                       \
    } while (0)

using cafey::core::ResetGuard;
using cafey::core::ResetKind;
using cafey::core::assess_reset_reason;

// Cafeteira testavel para acionar boot/dispatch sem task FreeRTOS.
class TestableCafeteira : public cafey::app::Cafeteira {
public:
    using Cafeteira::Cafeteira;
    void boot() { init(); on_start(); }
};

int test_map_reset_reason_to_code() {
    auto wdt = assess_reset_reason(ESP_RST_TASK_WDT);
    TEST_ASSERT(wdt.kind == ResetKind::Watchdog, "task wdt -> Watchdog");
    TEST_ASSERT(wdt.abnormal, "task wdt anormal");
    TEST_ASSERT(std::string(wdt.code) == "RESET_WATCHDOG", "codigo watchdog");

    auto iwdt = assess_reset_reason(ESP_RST_INT_WDT);
    TEST_ASSERT(std::string(iwdt.code) == "RESET_WATCHDOG", "int wdt -> watchdog");

    auto panic = assess_reset_reason(ESP_RST_PANIC);
    TEST_ASSERT(panic.kind == ResetKind::Panic, "panic -> Panic");
    TEST_ASSERT(std::string(panic.code) == "RESET_PANIC", "codigo panic");

    auto brown = assess_reset_reason(ESP_RST_BROWNOUT);
    TEST_ASSERT(brown.kind == ResetKind::Brownout, "brownout -> Brownout");
    TEST_ASSERT(std::string(brown.code) == "RESET_BROWNOUT", "codigo brownout");

    auto pwr = assess_reset_reason(ESP_RST_POWERON);
    TEST_ASSERT(!pwr.abnormal, "poweron normal");
    TEST_ASSERT(std::string(pwr.code) == "RESET_NORMAL", "codigo normal");
    auto sw = assess_reset_reason(ESP_RST_SW);
    TEST_ASSERT(!sw.abnormal, "sw reset normal");

    std::cout << "[PASS] test_map_reset_reason_to_code" << std::endl;
    return 0;
}

int test_abnormal_reset_counter_and_safe_mode_entry() {
    MockNvs::reset();
    {
        ResetGuard g;
        TEST_ASSERT(g.init() == ESP_OK, "init 1");
        g.record_boot(ESP_RST_TASK_WDT);
        TEST_ASSERT(g.abnormal_count() == 1, "contador 1");
        TEST_ASSERT(!g.safe_mode(), "sem safe mode com 1");
    }
    {
        ResetGuard g;
        TEST_ASSERT(g.init() == ESP_OK, "init 2");
        g.record_boot(ESP_RST_PANIC);
        TEST_ASSERT(g.abnormal_count() == 2, "contador 2 acumulado");
        TEST_ASSERT(!g.safe_mode(), "sem safe mode com 2");
    }
    {
        ResetGuard g;
        TEST_ASSERT(g.init() == ESP_OK, "init 3");
        g.record_boot(ESP_RST_BROWNOUT);
        TEST_ASSERT(g.abnormal_count() == 3, "contador 3 acumulado");
        TEST_ASSERT(g.safe_mode(), "safe mode com 3 resets anormais consecutivos");
    }
    std::cout << "[PASS] test_abnormal_reset_counter_and_safe_mode_entry" << std::endl;
    return 0;
}

int test_stable_period_clears_counter_and_allows_safe_mode_exit() {
    MockNvs::reset();
    // Acumula 3 resets anormais -> safe mode.
    for (int i = 0; i < 3; ++i) {
        ResetGuard g;
        TEST_ASSERT(g.init() == ESP_OK, "init anormal");
        g.record_boot(ESP_RST_TASK_WDT);
    }
    {
        ResetGuard g;
        TEST_ASSERT(g.init() == ESP_OK, "init antes estavel");
        TEST_ASSERT(g.safe_mode(), "safe mode ativo");
        TEST_ASSERT(g.mark_stable() == ESP_OK, "mark_stable");
        TEST_ASSERT(g.abnormal_count() == 0, "contador zerado apos estavel");
        TEST_ASSERT(!g.safe_mode(), "safe mode desarma apos estavel");
    }
    // Boot limpo apos periodo estavel: sai do modo seguro (contador zerado).
    {
        ResetGuard g;
        TEST_ASSERT(g.init() == ESP_OK, "init boot limpo");
        g.record_boot(ESP_RST_POWERON);
        TEST_ASSERT(g.abnormal_count() == 0, "contador segue 0");
        TEST_ASSERT(!g.safe_mode(), "boot limpo apos estavel fora de safe mode");
    }
    std::cout << "[PASS] test_stable_period_clears_counter_and_allows_safe_mode_exit" << std::endl;
    return 0;
}

int test_safe_mode_rejects_brew_commands_and_button() {
    MockGpio::reset();
    MockGpio::get_pin(GPIO_NUM_27).level = 1;
    TestableCafeteira caf;
    caf.boot();
    caf.set_safe_mode(true);

    // Comando de preparo recusado.
    caf.post(cafey::core::Message{cafey::core::MessageType::StartBrew, 60});
    caf.process_pending();
    TEST_ASSERT(caf.state() == cafey::app::Cafeteira::State::Off, "preparo nao inicia em safe mode");
    TEST_ASSERT(!caf.relay_on(), "rele permanece desligado");

    // Botao (toque curto -> StartBrew BOTAO) recusado.
    caf.post(cafey::core::Message{cafey::core::MessageType::ButtonPressed, 0});
    caf.process_pending();
    TEST_ASSERT(caf.state() == cafey::app::Cafeteira::State::Off, "botao nao inicia preparo em safe mode");

    std::cout << "[PASS] test_safe_mode_rejects_brew_commands_and_button" << std::endl;
    return 0;
}

int main() {
    MockTaskWdt::reset();
    int fails = 0;
    fails += test_map_reset_reason_to_code();
    fails += test_abnormal_reset_counter_and_safe_mode_entry();
    fails += test_stable_period_clears_counter_and_allows_safe_mode_exit();
    fails += test_safe_mode_rejects_brew_commands_and_button();
    if (fails != 0) {
        std::cerr << fails << " test(es) falharam" << std::endl;
        return 1;
    }
    std::cout << "Todos os testes de reset guard passaram." << std::endl;
    return 0;
}
