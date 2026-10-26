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
using cafey::drivers::LedPattern;
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

    // 1. Conectado e ocioso: Azul respirando lento (spec §5.3)
    led.update(CoffeeVisualState::Idle, true);
    TEST_ASSERT(led.color() == LedColor::Blue && pins_are(0, 0, 1), "ocioso conectado -> azul");
    TEST_ASSERT(led.pattern() == LedPattern::Breathe, "ocioso conectado -> respirando");
    TEST_ASSERT(!led.blinking(), "rede ok nao pisca");

    // 2. Agendamento armado conectado: Verde fixo (spec §5.3)
    led.update(CoffeeVisualState::ScheduleArmed, true);
    TEST_ASSERT(led.color() == LedColor::Green && pins_are(0, 1, 0), "agendado conectado -> verde");
    TEST_ASSERT(led.pattern() == LedPattern::Solid, "agendado conectado -> fixo");
    TEST_ASSERT(!led.blinking(), "agendado conectado nao pisca");

    // 3. Preparando cafe agora: Verde pulsando (spec §5.3)
    led.update(CoffeeVisualState::Brewing, true);
    TEST_ASSERT(led.color() == LedColor::Green && pins_are(0, 1, 0), "preparando -> verde");
    TEST_ASSERT(led.pattern() == LedPattern::Pulse, "preparando -> pulsando");

    // 4. Cafe pronto: Branco fixo por alguns minutos (spec §5.3)
    led.update(CoffeeVisualState::Ready, true);
    TEST_ASSERT(led.color() == LedColor::White && pins_are(1, 1, 1), "pronto -> branco");
    TEST_ASSERT(led.pattern() == LedPattern::Solid, "pronto -> fixo");

    // 5. Erro ou falha: Vermelho fixo (spec §5.3)
    led.update(CoffeeVisualState::Error, true);
    TEST_ASSERT(led.color() == LedColor::Red && pins_are(1, 0, 0), "erro -> vermelho");
    TEST_ASSERT(led.pattern() == LedPattern::Solid, "erro -> fixo");

    std::cout << "[PASS] test_color_maps_coffee_state_when_network_ok" << std::endl;
    return 0;
}

int test_schedule_armed_when_disconnected_is_amber_solid() {
    MockGpio::reset();
    RgbLed led;
    led.init();

    // Desconectado, com agendamento armado -> Ambar fixo (spec §5.3)
    led.update(CoffeeVisualState::ScheduleArmed, false);
    TEST_ASSERT(led.color() == LedColor::Amber, "desconectado com agendamento -> ambar");
    TEST_ASSERT(led.pattern() == LedPattern::Solid, "ambar deve ser FIXO (nao pisca)");
    TEST_ASSERT(!led.blinking(), "ambar fixo nao pisca");
    TEST_ASSERT(pins_are(1, 1, 0), "ambar = R + G acesos");

    led.refresh();
    TEST_ASSERT(pins_are(1, 1, 0), "ambar fixo permanece aceso apos refresh");

    std::cout << "[PASS] test_schedule_armed_when_disconnected_is_amber_solid" << std::endl;
    return 0;
}

int test_idle_without_network_blinks_amber() {
    MockGpio::reset();
    RgbLed led;
    led.init();

    // Sem Wi-Fi, ocioso -> Amarelo piscando (spec §5.3)
    led.update(CoffeeVisualState::Idle, false);
    TEST_ASSERT(led.color() == LedColor::Amber, "sem rede + ocioso -> amarelo/ambar");
    TEST_ASSERT(led.pattern() == LedPattern::Blink, "sem rede + ocioso -> piscando");
    TEST_ASSERT(led.blinking(), "blinking() deve ser true");
    TEST_ASSERT(led.lit() && pins_are(1, 1, 0), "comeca aceso");

    led.refresh();
    TEST_ASSERT(!led.lit() && pins_are(0, 0, 0), "refresh apaga o LED (piscada)");
    led.refresh();
    TEST_ASSERT(led.lit() && pins_are(1, 1, 0), "refresh volta a acender");

    std::cout << "[PASS] test_idle_without_network_blinks_amber" << std::endl;
    return 0;
}

int test_error_stays_solid_even_without_network() {
    MockGpio::reset();
    RgbLed led;
    led.init();

    led.update(CoffeeVisualState::Error, false);
    TEST_ASSERT(led.color() == LedColor::Red, "erro -> vermelho");
    TEST_ASSERT(led.pattern() == LedPattern::Solid, "erro e fixo");
    TEST_ASSERT(!led.blinking(), "erro nao pisca");
    led.refresh();
    TEST_ASSERT(pins_are(1, 0, 0), "vermelho permanece fixo apos refresh");

    std::cout << "[PASS] test_error_stays_solid_even_without_network" << std::endl;
    return 0;
}

int test_tick_animation_timing() {
    MockGpio::reset();
    RgbLed led;
    led.init();

    // 1. Teste de Blink (1 Hz: 50 ticks aceso, 50 ticks apagado a 10 ms/tick)
    led.update(CoffeeVisualState::Idle, false); // Amarelo piscando
    TEST_ASSERT(pins_are(1, 1, 0), "comeca aceso");
    for (int i = 0; i < 49; ++i) led.tick();
    TEST_ASSERT(pins_are(1, 1, 0), "continua aceso ate tick 49");
    led.tick(); // tick 50
    TEST_ASSERT(pins_are(0, 0, 0), "apaga no tick 50");
    for (int i = 0; i < 49; ++i) led.tick();
    TEST_ASSERT(pins_are(0, 0, 0), "permanece apagado");
    led.tick(); // tick 100
    TEST_ASSERT(pins_are(1, 1, 0), "reacende no tick 100");

    // 2. Teste de Pulse (2 Hz: 25 ticks aceso, 25 ticks apagado)
    led.update(CoffeeVisualState::Brewing, true); // Verde pulsando
    TEST_ASSERT(pins_are(0, 1, 0), "comeca aceso verde");
    for (int i = 0; i < 24; ++i) led.tick();
    TEST_ASSERT(pins_are(0, 1, 0), "verde aceso ate tick 24");
    led.tick(); // tick 25
    TEST_ASSERT(pins_are(0, 0, 0), "verde apaga no tick 25");
    for (int i = 0; i < 24; ++i) led.tick();
    led.tick(); // tick 50
    TEST_ASSERT(pins_are(0, 1, 0), "verde reacende no tick 50");

    std::cout << "[PASS] test_tick_animation_timing" << std::endl;
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
    std::cout << "Running Cafey RgbLed Driver Unit Tests (Spec §5.3)..." << std::endl;
    if (test_init_configures_pins_and_starts_off()) return 1;
    if (test_color_maps_coffee_state_when_network_ok()) return 1;
    if (test_schedule_armed_when_disconnected_is_amber_solid()) return 1;
    if (test_idle_without_network_blinks_amber()) return 1;
    if (test_error_stays_solid_even_without_network()) return 1;
    if (test_tick_animation_timing()) return 1;
    if (test_common_anode_inverts_levels()) return 1;
    std::cout << "All RgbLed driver tests PASSED!" << std::endl;
    return 0;
}
