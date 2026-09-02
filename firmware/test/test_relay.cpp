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

int test_initialization() {
    MockGpio::reset();
    cafey::drivers::Relay relay(GPIO_NUM_26);

    TEST_ASSERT(!relay.is_initialized(), "Relay should not be initialized initially when auto_init is false");
    TEST_ASSERT(!relay.is_on(), "Relay should be OFF initially");
    TEST_ASSERT(relay.get_pin() == GPIO_NUM_26, "Pin should be GPIO_NUM_26");

    esp_err_t err = relay.init();
    TEST_ASSERT(err == ESP_OK, "init() should return ESP_OK");
    TEST_ASSERT(relay.is_initialized(), "Relay should be initialized after init()");
    TEST_ASSERT(!relay.is_on(), "Relay should still be OFF after init()");

    const auto& pin_state = MockGpio::get_pin(GPIO_NUM_26);
    TEST_ASSERT(pin_state.is_configured, "GPIO 26 should be configured");
    TEST_ASSERT(pin_state.mode == GPIO_MODE_OUTPUT, "GPIO 26 should be in OUTPUT mode");
    TEST_ASSERT(pin_state.pull_up == GPIO_PULLUP_DISABLE, "Internal pull-up should be disabled");
    TEST_ASSERT(pin_state.pull_down == GPIO_PULLDOWN_DISABLE, "Internal pull-down should be disabled (R1 handles external pull-down)");
    TEST_ASSERT(pin_state.level == 0, "GPIO level should be 0 (LOW)");

    // Verify anti-glitch sequence: gpio_set_level(26, 0) was called BEFORE and AFTER gpio_config
    const auto& history = MockGpio::get_history();
    TEST_ASSERT(history.size() >= 3, "History should contain pre-config set_level, config, and post-config set_level");
    TEST_ASSERT(history[0] == "gpio_set_level:26=0", "First call must force LOW before config");
    TEST_ASSERT(history[1] == "gpio_config:26", "Second call must configure GPIO");
    TEST_ASSERT(history[2] == "gpio_set_level:26=0", "Third call must re-assert LOW");

    std::cout << "[PASS] test_initialization" << std::endl;
    return 0;
}

int test_auto_init_constructor() {
    MockGpio::reset();
    {
        cafey::drivers::Relay relay(GPIO_NUM_26, true);
        TEST_ASSERT(relay.is_initialized(), "Relay with auto_init=true should be initialized");
        TEST_ASSERT(!relay.is_on(), "Relay should be OFF after auto_init");
        TEST_ASSERT(MockGpio::get_pin(GPIO_NUM_26).level == 0, "Pin level should be 0");
    }
    std::cout << "[PASS] test_auto_init_constructor" << std::endl;
    return 0;
}

int test_invalid_pin() {
    MockGpio::reset();
    // Test input-only pin on ESP32 (GPIO 34)
    cafey::drivers::Relay relay_input_only(GPIO_NUM_34);
    TEST_ASSERT(relay_input_only.init() == ESP_ERR_INVALID_ARG, "init() must reject input-only pins (34-39)");

    // Test out-of-range pin
    cafey::drivers::Relay relay_invalid(GPIO_NUM_MAX);
    TEST_ASSERT(relay_invalid.init() == ESP_ERR_INVALID_ARG, "init() must reject out-of-range pins");

    std::cout << "[PASS] test_invalid_pin" << std::endl;
    return 0;
}

int test_uninitialized_operations() {
    MockGpio::reset();
    cafey::drivers::Relay relay(GPIO_NUM_26);

    TEST_ASSERT(relay.set(true) == ESP_ERR_INVALID_STATE, "set() before init() must return ESP_ERR_INVALID_STATE");
    TEST_ASSERT(relay.turn_on() == ESP_ERR_INVALID_STATE, "turn_on() before init() must return ESP_ERR_INVALID_STATE");
    TEST_ASSERT(relay.turn_off() == ESP_ERR_INVALID_STATE, "turn_off() before init() must return ESP_ERR_INVALID_STATE");
    TEST_ASSERT(relay.toggle() == ESP_ERR_INVALID_STATE, "toggle() before init() must return ESP_ERR_INVALID_STATE");

    std::cout << "[PASS] test_uninitialized_operations" << std::endl;
    return 0;
}

int test_state_transitions() {
    MockGpio::reset();
    cafey::drivers::Relay relay(GPIO_NUM_26);
    relay.init();

    // Turn ON (Active HIGH -> pin level = 1)
    esp_err_t err = relay.turn_on();
    TEST_ASSERT(err == ESP_OK, "turn_on() should succeed");
    TEST_ASSERT(relay.is_on(), "relay.is_on() should be true");
    TEST_ASSERT(MockGpio::get_pin(GPIO_NUM_26).level == 1, "GPIO 26 level should be 1 (HIGH)");

    // Turn OFF (Active HIGH -> pin level = 0)
    err = relay.turn_off();
    TEST_ASSERT(err == ESP_OK, "turn_off() should succeed");
    TEST_ASSERT(!relay.is_on(), "relay.is_on() should be false");
    TEST_ASSERT(MockGpio::get_pin(GPIO_NUM_26).level == 0, "GPIO 26 level should be 0 (LOW)");

    // Toggle OFF -> ON
    err = relay.toggle();
    TEST_ASSERT(err == ESP_OK, "toggle() should succeed");
    TEST_ASSERT(relay.is_on(), "relay.is_on() should be true");
    TEST_ASSERT(MockGpio::get_pin(GPIO_NUM_26).level == 1, "GPIO 26 level should be 1");

    // Toggle ON -> OFF
    err = relay.toggle();
    TEST_ASSERT(err == ESP_OK, "toggle() should succeed");
    TEST_ASSERT(!relay.is_on(), "relay.is_on() should be false");
    TEST_ASSERT(MockGpio::get_pin(GPIO_NUM_26).level == 0, "GPIO 26 level should be 0");

    // Direct set(true) and set(false)
    relay.set(true);
    TEST_ASSERT(relay.is_on() && MockGpio::get_pin(GPIO_NUM_26).level == 1, "set(true) should turn relay ON");
    relay.set(false);
    TEST_ASSERT(!relay.is_on() && MockGpio::get_pin(GPIO_NUM_26).level == 0, "set(false) should turn relay OFF");

    std::cout << "[PASS] test_state_transitions" << std::endl;
    return 0;
}

int test_raii_destruction() {
    MockGpio::reset();
    {
        cafey::drivers::Relay relay(GPIO_NUM_26);
        relay.init();
        relay.turn_on();
        TEST_ASSERT(MockGpio::get_pin(GPIO_NUM_26).level == 1, "Relay should be ON");
    }
    // Out of scope: destructor should turn relay OFF
    TEST_ASSERT(MockGpio::get_pin(GPIO_NUM_26).level == 0, "Relay destructor must safely turn off relay");

    std::cout << "[PASS] test_raii_destruction" << std::endl;
    return 0;
}

int test_move_semantics() {
    MockGpio::reset();
    {
        cafey::drivers::Relay r1(GPIO_NUM_26);
        r1.init();
        r1.turn_on();
        TEST_ASSERT(r1.is_on(), "r1 should be ON");

        // Move construct
        cafey::drivers::Relay r2(std::move(r1));
        TEST_ASSERT(!r1.is_initialized(), "r1 should no longer be initialized");
        TEST_ASSERT(r2.is_initialized(), "r2 should be initialized");
        TEST_ASSERT(r2.is_on(), "r2 should be ON");
        TEST_ASSERT(r2.get_pin() == GPIO_NUM_26, "r2 should have pin GPIO_NUM_26");

        // Move assign
        cafey::drivers::Relay r3(GPIO_NUM_25);
        r3.init();
        r3.turn_on();
        r3 = std::move(r2);
        TEST_ASSERT(r3.is_initialized(), "r3 should be initialized");
        TEST_ASSERT(r3.is_on(), "r3 should be ON");
        TEST_ASSERT(r3.get_pin() == GPIO_NUM_26, "r3 should have pin GPIO_NUM_26");
    }
    // All destroyed
    TEST_ASSERT(MockGpio::get_pin(GPIO_NUM_26).level == 0, "GPIO 26 should be 0 (OFF)");

    std::cout << "[PASS] test_move_semantics" << std::endl;
    return 0;
}

int test_hardware_error_handling() {
    MockGpio::reset();
    MockGpio::config_ret_code = ESP_FAIL;
    cafey::drivers::Relay relay(GPIO_NUM_26);
    TEST_ASSERT(relay.init() == ESP_FAIL, "init() must return error code if gpio_config fails");
    TEST_ASSERT(!relay.is_initialized(), "Relay should not be marked initialized on config error");

    MockGpio::reset();
    relay.init();
    MockGpio::set_level_ret_code = ESP_FAIL;
    TEST_ASSERT(relay.turn_on() == ESP_FAIL, "turn_on() must return error code if gpio_set_level fails");
    TEST_ASSERT(!relay.is_on(), "is_on() should not change if gpio_set_level fails");

    std::cout << "[PASS] test_hardware_error_handling" << std::endl;
    return 0;
}

int main() {
    std::cout << "Running Cafey Relay Driver Unit Tests..." << std::endl;

    if (test_initialization()) return 1;
    if (test_auto_init_constructor()) return 1;
    if (test_invalid_pin()) return 1;
    if (test_uninitialized_operations()) return 1;
    if (test_state_transitions()) return 1;
    if (test_raii_destruction()) return 1;
    if (test_move_semantics()) return 1;
    if (test_hardware_error_handling()) return 1;

    std::cout << "All 8 Relay Driver tests PASSED!" << std::endl;
    return 0;
}
