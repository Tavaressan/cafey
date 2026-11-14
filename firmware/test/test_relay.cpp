#include <cassert>
#include <iostream>
#include <vector>
#include <string>
#include "mock_esp_gpio.hpp"
#include "relay.hpp"

#define TEST_ASSERT(cond, msg) \
    do { \
        if (!(cond)) { \
            std::cerr << "FAILED: " << msg << " (" << __FILE__ << ":" << __LINE__ << ")" << std::endl; \
            return 1; \
        } \
    } while (0)

using cafey::drivers::Relay;
using ActiveLevel = cafey::drivers::Relay::ActiveLevel;

namespace {

// HIGH liga o SSR-40DA (default); LOW liga o HL-52S (legado). HW-27, #305.
int on_level(ActiveLevel al) { return al == ActiveLevel::High ? 1 : 0; }
int off_level(ActiveLevel al) { return al == ActiveLevel::High ? 0 : 1; }
const char* name(ActiveLevel al) { return al == ActiveLevel::High ? "active-high" : "active-low"; }
std::string set_evt(int pin, int level) {
    return "gpio_set_level:" + std::to_string(pin) + "=" + std::to_string(level);
}

} // namespace

int test_default_polarity_is_active_high() {
    MockGpio::reset();
    Relay relay(GPIO_NUM_26);
    TEST_ASSERT(relay.active_level() == ActiveLevel::High, "Default polarity must be active-high (SSR-40DA)");

    relay.init();
    TEST_ASSERT(MockGpio::get_pin(GPIO_NUM_26).level == 0, "Default relay must idle LOW (SSR OFF)");
    relay.turn_on();
    TEST_ASSERT(MockGpio::get_pin(GPIO_NUM_26).level == 1, "Default relay must drive HIGH to turn the SSR ON");

    std::cout << "[PASS] test_default_polarity_is_active_high" << std::endl;
    return 0;
}

int test_initialization(ActiveLevel al) {
    MockGpio::reset();
    Relay relay(GPIO_NUM_26, false, al);

    TEST_ASSERT(!relay.is_initialized(), "Relay should not be initialized initially when auto_init is false");
    TEST_ASSERT(!relay.is_on(), "Relay should be OFF initially");
    TEST_ASSERT(relay.get_pin() == GPIO_NUM_26, "Pin should be GPIO_NUM_26");
    TEST_ASSERT(relay.active_level() == al, "Polarity must be the one passed to the constructor");

    esp_err_t err = relay.init();
    TEST_ASSERT(err == ESP_OK, "init() should return ESP_OK");
    TEST_ASSERT(relay.is_initialized(), "Relay should be initialized after init()");
    TEST_ASSERT(!relay.is_on(), "Relay should still be OFF after init()");

    const auto& pin_state = MockGpio::get_pin(GPIO_NUM_26);
    TEST_ASSERT(pin_state.is_configured, "GPIO 26 should be configured");
    TEST_ASSERT(pin_state.mode == GPIO_MODE_OUTPUT, "GPIO 26 should be in OUTPUT mode");
    TEST_ASSERT(pin_state.pull_up == GPIO_PULLUP_DISABLE, "Internal pull-up should be disabled (external R1)");
    TEST_ASSERT(pin_state.pull_down == GPIO_PULLDOWN_DISABLE, "Internal pull-down should be disabled (external R1)");
    TEST_ASSERT(pin_state.level == off_level(al), "GPIO level must be the OFF level after init()");

    // Anti-glitch: OFF level forced BEFORE and AFTER gpio_config.
    const auto& history = MockGpio::get_history();
    TEST_ASSERT(history.size() == 3, "init() must be exactly: set OFF, config, set OFF");
    TEST_ASSERT(history[0] == set_evt(26, off_level(al)), "First call must force the OFF level before config");
    TEST_ASSERT(history[1] == "gpio_config:26", "Second call must configure GPIO");
    TEST_ASSERT(history[2] == set_evt(26, off_level(al)), "Third call must re-assert the OFF level");

    std::cout << "[PASS] test_initialization (" << name(al) << ")" << std::endl;
    return 0;
}

int test_no_on_pulse_during_boot(ActiveLevel al) {
    // Boot, reset (EN) e gravacao passam por init(): em nenhum momento o pino
    // pode ir para o nivel que liga a carga.
    MockGpio::reset();
    {
        Relay relay(GPIO_NUM_26, true, al);
        TEST_ASSERT(relay.is_initialized(), "auto_init must initialize");
    }
    for (const auto& evt : MockGpio::get_history()) {
        TEST_ASSERT(evt != set_evt(26, on_level(al)), "init()/destructor must never drive the ON level");
    }

    std::cout << "[PASS] test_no_on_pulse_during_boot (" << name(al) << ")" << std::endl;
    return 0;
}

int test_auto_init_constructor(ActiveLevel al) {
    MockGpio::reset();
    {
        Relay relay(GPIO_NUM_26, true, al);
        TEST_ASSERT(relay.is_initialized(), "Relay with auto_init=true should be initialized");
        TEST_ASSERT(!relay.is_on(), "Relay should be OFF after auto_init");
        TEST_ASSERT(MockGpio::get_pin(GPIO_NUM_26).level == off_level(al), "Pin must be at the OFF level");
    }
    std::cout << "[PASS] test_auto_init_constructor (" << name(al) << ")" << std::endl;
    return 0;
}

int test_invalid_pin() {
    MockGpio::reset();
    // Test input-only pin on ESP32 (GPIO 34)
    Relay relay_input_only(GPIO_NUM_34);
    TEST_ASSERT(relay_input_only.init() == ESP_ERR_INVALID_ARG, "init() must reject input-only pins (34-39)");

    // Test out-of-range pin
    Relay relay_invalid(GPIO_NUM_MAX);
    TEST_ASSERT(relay_invalid.init() == ESP_ERR_INVALID_ARG, "init() must reject out-of-range pins");

    std::cout << "[PASS] test_invalid_pin" << std::endl;
    return 0;
}

int test_uninitialized_operations() {
    MockGpio::reset();
    Relay relay(GPIO_NUM_26);

    TEST_ASSERT(relay.set(true) == ESP_ERR_INVALID_STATE, "set() before init() must return ESP_ERR_INVALID_STATE");
    TEST_ASSERT(relay.turn_on() == ESP_ERR_INVALID_STATE, "turn_on() before init() must return ESP_ERR_INVALID_STATE");
    TEST_ASSERT(relay.turn_off() == ESP_ERR_INVALID_STATE, "turn_off() before init() must return ESP_ERR_INVALID_STATE");
    TEST_ASSERT(relay.toggle() == ESP_ERR_INVALID_STATE, "toggle() before init() must return ESP_ERR_INVALID_STATE");
    TEST_ASSERT(MockGpio::get_history().empty(), "No GPIO access before init()");

    std::cout << "[PASS] test_uninitialized_operations" << std::endl;
    return 0;
}

int test_state_transitions(ActiveLevel al) {
    MockGpio::reset();
    Relay relay(GPIO_NUM_26, false, al);
    relay.init();
    const auto level = [] { return MockGpio::get_pin(GPIO_NUM_26).level; };

    esp_err_t err = relay.turn_on();
    TEST_ASSERT(err == ESP_OK, "turn_on() should succeed");
    TEST_ASSERT(relay.is_on(), "relay.is_on() should be true");
    TEST_ASSERT(level() == on_level(al), "turn_on() must drive the ON level");

    err = relay.turn_off();
    TEST_ASSERT(err == ESP_OK, "turn_off() should succeed");
    TEST_ASSERT(!relay.is_on(), "relay.is_on() should be false");
    TEST_ASSERT(level() == off_level(al), "turn_off() must drive the OFF level");

    err = relay.toggle();
    TEST_ASSERT(err == ESP_OK, "toggle() should succeed");
    TEST_ASSERT(relay.is_on() && level() == on_level(al), "toggle() OFF -> ON");

    err = relay.toggle();
    TEST_ASSERT(err == ESP_OK, "toggle() should succeed");
    TEST_ASSERT(!relay.is_on() && level() == off_level(al), "toggle() ON -> OFF");

    relay.set(true);
    TEST_ASSERT(relay.is_on() && level() == on_level(al), "set(true) should turn relay ON");
    relay.set(false);
    TEST_ASSERT(!relay.is_on() && level() == off_level(al), "set(false) should turn relay OFF");

    std::cout << "[PASS] test_state_transitions (" << name(al) << ")" << std::endl;
    return 0;
}

int test_raii_destruction(ActiveLevel al) {
    MockGpio::reset();
    {
        Relay relay(GPIO_NUM_26, false, al);
        relay.init();
        relay.turn_on();
        TEST_ASSERT(MockGpio::get_pin(GPIO_NUM_26).level == on_level(al), "Relay should be ON");
    }
    TEST_ASSERT(MockGpio::get_pin(GPIO_NUM_26).level == off_level(al), "Relay destructor must safely turn off relay");

    std::cout << "[PASS] test_raii_destruction (" << name(al) << ")" << std::endl;
    return 0;
}

int test_move_semantics(ActiveLevel al) {
    MockGpio::reset();
    {
        Relay r1(GPIO_NUM_26, false, al);
        r1.init();
        r1.turn_on();
        TEST_ASSERT(r1.is_on(), "r1 should be ON");

        // Move construct
        Relay r2(std::move(r1));
        TEST_ASSERT(!r1.is_initialized(), "r1 should no longer be initialized");
        TEST_ASSERT(r2.is_initialized(), "r2 should be initialized");
        TEST_ASSERT(r2.is_on(), "r2 should be ON");
        TEST_ASSERT(r2.get_pin() == GPIO_NUM_26, "r2 should have pin GPIO_NUM_26");
        TEST_ASSERT(r2.active_level() == al, "r2 must keep the polarity");

        // Move assign onto a relay with the other polarity: r3 turns its own
        // pin OFF with its own polarity, then takes r2's pin and polarity.
        const ActiveLevel other = al == ActiveLevel::High ? ActiveLevel::Low : ActiveLevel::High;
        Relay r3(GPIO_NUM_25, false, other);
        r3.init();
        r3.turn_on();
        r3 = std::move(r2);
        TEST_ASSERT(MockGpio::get_pin(GPIO_NUM_25).level == off_level(other), "Old r3 pin must be turned OFF with its own polarity");
        TEST_ASSERT(r3.is_initialized(), "r3 should be initialized");
        TEST_ASSERT(r3.is_on(), "r3 should be ON");
        TEST_ASSERT(r3.get_pin() == GPIO_NUM_26, "r3 should have pin GPIO_NUM_26");
        TEST_ASSERT(r3.active_level() == al, "r3 must take r2's polarity");
    }
    TEST_ASSERT(MockGpio::get_pin(GPIO_NUM_26).level == off_level(al), "GPIO 26 must end OFF");

    std::cout << "[PASS] test_move_semantics (" << name(al) << ")" << std::endl;
    return 0;
}

int test_hardware_error_handling() {
    MockGpio::reset();
    MockGpio::config_ret_code = ESP_FAIL;
    Relay relay(GPIO_NUM_26);
    TEST_ASSERT(relay.init() == ESP_FAIL, "init() must return error code if gpio_config fails");
    TEST_ASSERT(!relay.is_initialized(), "Relay should not be marked initialized on config error");

    MockGpio::reset();
    relay.init();
    MockGpio::set_level_ret_code = ESP_FAIL;
    TEST_ASSERT(relay.turn_on() == ESP_FAIL, "turn_on() must return error code if gpio_set_level fails");
    TEST_ASSERT(!relay.is_on(), "is_on() should not change if gpio_set_level fails");
    MockGpio::set_level_ret_code = ESP_OK;

    std::cout << "[PASS] test_hardware_error_handling" << std::endl;
    return 0;
}

int main() {
    std::cout << "Running Cafey Relay Driver Unit Tests..." << std::endl;

    int tests = 0;
    if (test_default_polarity_is_active_high()) return 1; ++tests;
    if (test_invalid_pin()) return 1; ++tests;
    if (test_uninitialized_operations()) return 1; ++tests;
    if (test_hardware_error_handling()) return 1; ++tests;

    for (const ActiveLevel al : {ActiveLevel::High, ActiveLevel::Low}) {
        if (test_initialization(al)) return 1; ++tests;
        if (test_no_on_pulse_during_boot(al)) return 1; ++tests;
        if (test_auto_init_constructor(al)) return 1; ++tests;
        if (test_state_transitions(al)) return 1; ++tests;
        if (test_raii_destruction(al)) return 1; ++tests;
        if (test_move_semantics(al)) return 1; ++tests;
    }

    std::cout << "All " << tests << " Relay Driver tests PASSED!" << std::endl;
    return 0;
}
