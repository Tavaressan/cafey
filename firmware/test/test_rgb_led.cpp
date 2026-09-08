#include <iostream>

#include "drivers/rgb_led.hpp"

#define TEST_ASSERT(cond, msg)                                                  \
    do {                                                                       \
        if (!(cond)) {                                                         \
            std::cerr << "FAILED: " << msg << " (" << __FILE__ << ":"          \
                      << __LINE__ << ")" << std::endl;                         \
            return 1;                                                          \
        }                                                                      \
    } while (0)

using cafey::drivers::CoffeeVisualState;
using cafey::drivers::LedColor;
using cafey::drivers::RgbLed;

namespace {
constexpr gpio_num_t R = GPIO_NUM_21;
constexpr gpio_num_t G = GPIO_NUM_22;
constexpr gpio_num_t B = GPIO_NUM_23;

bool pins_are(int r, int g, int b) {
    return MockGpio::get_pin(R).level == r &&
           MockGpio::get_pin(G).level == g &&
           MockGpio::get_pin(B).level == b;
}
} // namespace

int test_init_configures_pins_and_starts_off() {
    MockGpio::reset();
    RgbLed led;
    TEST_ASSERT(led.init() == ESP_OK, "init deve retornar ESP_OK");
    TEST_ASSERT(MockGpio::get_pin(R).mode == GPIO_MODE_OUTPUT, "R como saida");
    TEST_ASSERT(MockGpio::get_pin(G).mode == GPIO_MODE_OUTPUT, "G como saida");
    TEST_ASSERT(MockGpio::get_pin(B).mode == GPIO_MODE_OUTPUT, "B como saida");
    TEST_ASSERT(led.color() == LedColor::Off, "LED inicia apagado");
    TEST_ASSERT(pins_are(0, 0, 0), "todos os canais apagados");
    std::cout << "[PASS] test_init_configures_pins_and_starts_off" << std::endl;
    return 0;
}

int test_color_maps_coffee_state_when_network_ok() {
    MockGpio::reset();
    RgbLed led;
    led.init();

    led.update(CoffeeVisualState::Idle, true);
    TEST_ASSERT(led.color() == LedColor::Blue && pins_are(0, 0, 1), "ocioso -> azul");
    TEST_ASSERT(!led.blinking(), "rede ok nao pisca");

    led.update(CoffeeVisualState::Brewing, true);
    TEST_ASSERT(led.color() == LedColor::Green && pins_are(0, 1, 0), "preparando -> verde");

    led.update(CoffeeVisualState::Ready, true);
    TEST_ASSERT(led.color() == LedColor::White && pins_are(1, 1, 1), "pronto -> branco");

    led.update(CoffeeVisualState::Error, true);
    TEST_ASSERT(led.color() == LedColor::Red && pins_are(1, 0, 0), "erro -> vermelho");

    std::cout << "[PASS] test_color_maps_coffee_state_when_network_ok" << std::endl;
    return 0;
}

int test_network_problem_makes_led_blink() {
    MockGpio::reset();
    RgbLed led;
    led.init();

    led.update(CoffeeVisualState::Idle, false);
    TEST_ASSERT(led.color() == LedColor::Amber, "sem rede + ocioso -> ambar");
    TEST_ASSERT(led.blinking(), "problema de rede faz piscar");
    TEST_ASSERT(led.lit() && pins_are(1, 1, 0), "comeca aceso");

    led.refresh();
    TEST_ASSERT(!led.lit() && pins_are(0, 0, 0), "refresh apaga o LED (piscada)");
    led.refresh();
    TEST_ASSERT(led.lit() && pins_are(1, 1, 0), "refresh volta a acender");

    std::cout << "[PASS] test_network_problem_makes_led_blink" << std::endl;
    return 0;
}

int test_error_stays_solid_even_without_network() {
    MockGpio::reset();
    RgbLed led;
    led.init();

    led.update(CoffeeVisualState::Error, false);
    TEST_ASSERT(led.color() == LedColor::Red, "erro -> vermelho");
    TEST_ASSERT(!led.blinking(), "erro nao pisca");
    led.refresh();
    TEST_ASSERT(pins_are(1, 0, 0), "vermelho permanece fixo apos refresh");

    std::cout << "[PASS] test_error_stays_solid_even_without_network" << std::endl;
    return 0;
}

int test_common_anode_inverts_levels() {
    MockGpio::reset();
    RgbLed led(R, G, B, /*common_anode=*/true);
    led.init();
    led.update(CoffeeVisualState::Brewing, true); // verde
    TEST_ASSERT(pins_are(1, 0, 1), "anodo comum: verde acende com G em nivel baixo");
    std::cout << "[PASS] test_common_anode_inverts_levels" << std::endl;
    return 0;
}

int main() {
    std::cout << "Running Cafey RgbLed Driver Unit Tests..." << std::endl;
    if (test_init_configures_pins_and_starts_off()) return 1;
    if (test_color_maps_coffee_state_when_network_ok()) return 1;
    if (test_network_problem_makes_led_blink()) return 1;
    if (test_error_stays_solid_even_without_network()) return 1;
    if (test_common_anode_inverts_levels()) return 1;
    std::cout << "All RgbLed driver tests PASSED!" << std::endl;
    return 0;
}
