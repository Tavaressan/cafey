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
constexpr ledc_channel_t kBlueChannel = LEDC_CHANNEL_0;

// Canal azul e controlado por LEDC: "aceso" = duty maximo (ou zero em anodo comum).
bool blue_lit(bool common_anode) {
    const uint32_t duty = MockLedc::get_duty(kBlueChannel);
    return common_anode ? duty == 0 : duty == 255;
}

bool pins_are(int r, int g, int b, bool common_anode = false) {
    return MockGpio::get_pin(R).level == r &&
           MockGpio::get_pin(G).level == g &&
           blue_lit(common_anode) == (b != 0);
}
} // namespace

int test_init_configures_pins_and_starts_off() {
    MockGpio::reset();
    MockLedc::reset();
    RgbLed led;
    TEST_ASSERT(led.init() == ESP_OK, "init deve retornar ESP_OK");
    TEST_ASSERT(MockGpio::get_pin(R).mode == GPIO_MODE_OUTPUT, "R como saida");
    TEST_ASSERT(MockGpio::get_pin(G).mode == GPIO_MODE_OUTPUT, "G como saida");
    TEST_ASSERT(MockLedc::get_pin(kBlueChannel) == B, "B controlado por LEDC no GPIO 23");
    TEST_ASSERT(MockLedc::timer_freq_hz == 5000, "PWM do azul em 5 kHz");
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
    TEST_ASSERT(led.color() == LedColor::Blue, "ocioso conectado -> azul");
    TEST_ASSERT(led.pattern() == LedPattern::Breathe, "ocioso conectado -> respirando");
    TEST_ASSERT(pins_are(0, 0, 0), "respiracao comeca apagada (sobe no primeiro tick)");
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

    // 6. Janela de vinculo aberta (FW-24): Azul piscando.
    led.update(CoffeeVisualState::Binding, true);
    TEST_ASSERT(led.color() == LedColor::Blue && pins_are(0, 0, 1), "vinculo -> azul");
    TEST_ASSERT(led.pattern() == LedPattern::Blink, "vinculo -> piscando");

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

int test_breathe_ramps_smoothly_without_flicker() {
    MockGpio::reset();
    MockLedc::reset();
    RgbLed led;
    led.init();
    led.update(CoffeeVisualState::Idle, true); // azul respirando

    // Ciclo de 240 ticks (2,4 s): sobe ate o pico no tick 120 e desce ate zero no 240.
    // Brilho nunca deve oscilar entre subida e descida (sem cintilacao).
    uint32_t prev = MockLedc::get_duty(kBlueChannel);
    TEST_ASSERT(prev == 0, "comeca em zero");
    for (int t = 1; t <= 120; ++t) {
        led.tick();
        const uint32_t d = MockLedc::get_duty(kBlueChannel);
        TEST_ASSERT(d >= prev, "subida monotona no primeiro meio ciclo");
        prev = d;
    }
    TEST_ASSERT(prev == 255, "pico maximo no tick 120");
    for (int t = 121; t <= 240; ++t) {
        led.tick();
        const uint32_t d = MockLedc::get_duty(kBlueChannel);
        TEST_ASSERT(d <= prev, "descida monotona no segundo meio ciclo");
        prev = d;
    }
    TEST_ASSERT(prev == 0, "volta a zero no tick 240");

    // Passos suaves: nenhuma variacao maior que 8 niveis por tick de 10 ms.
    uint32_t last = 0;
    for (int t = 1; t <= 120; ++t) {
        led.tick();
        const uint32_t d = MockLedc::get_duty(kBlueChannel);
        const uint32_t step = d > last ? d - last : last - d;
        TEST_ASSERT(step <= 8, "variacao de brilho suave entre ticks");
        last = d;
    }

    // Continuidade no wrap do contador (1200 ticks): a fase da respiracao nao salta.
    // Apos cruzar o wrap, o tick 1320 (contador 120) deve estar no pico, como no tick 120.
    MockLedc::reset();
    MockGpio::reset();
    RgbLed wrap_led;
    wrap_led.init();
    wrap_led.update(CoffeeVisualState::Idle, true);
    for (int t = 0; t < 1200; ++t) wrap_led.tick();        // contador volta a 0
    TEST_ASSERT(MockLedc::get_duty(kBlueChannel) == 0, "tick 1200 (fase 0) em zero");
    for (int t = 0; t < 120; ++t) wrap_led.tick();          // contador 120
    TEST_ASSERT(MockLedc::get_duty(kBlueChannel) == 255, "pico preservado apos o wrap");

    std::cout << "[PASS] test_breathe_ramps_smoothly_without_flicker" << std::endl;
    return 0;
}

int test_common_anode_inverts_levels() {
    MockGpio::reset();
    MockLedc::reset();
    RgbLed led(R, G, B, /*common_anode=*/true);
    led.init();
    led.update(CoffeeVisualState::Brewing, true); // verde
    TEST_ASSERT(MockLedc::get_duty(kBlueChannel) == 255, "anodo comum: verde com azul apagado (duty 255)");
    TEST_ASSERT(MockGpio::get_pin(G).level == 0, "anodo comum: verde acende com G em nivel baixo");
    led.update(CoffeeVisualState::Ready, true); // branco: R,G,B acesos
    TEST_ASSERT(MockGpio::get_pin(R).level == 0 && MockGpio::get_pin(G).level == 0,
                "anodo comum: R e G acesos em nivel baixo no branco");
    TEST_ASSERT(MockLedc::get_duty(kBlueChannel) == 0, "anodo comum: azul aceso com duty 0");
    std::cout << "[PASS] test_common_anode_inverts_levels" << std::endl;
    return 0;
}

int test_urgent_pulse_is_faster_and_resets_on_update() {
    MockGpio::reset();
    MockLedc::reset();
    RgbLed led;
    led.init();
    led.update(CoffeeVisualState::Brewing, true); // verde pulsando (normal, 50 ticks)
    TEST_ASSERT(!led.urgent(), "update comeca em ritmo normal");

    led.set_urgent(true);
    TEST_ASSERT(led.urgent(), "set_urgent liga o modo");
    for (int i = 0; i < 11; ++i) led.tick(); // ticks 1..11
    TEST_ASSERT(pins_are(0, 1, 0), "urgente: aceso ate o tick 11");
    led.tick(); // tick 12
    TEST_ASSERT(pins_are(0, 0, 0), "urgente: apaga no tick 12 (ciclo de 24)");
    for (int i = 0; i < 11; ++i) led.tick();
    TEST_ASSERT(pins_are(0, 0, 0), "urgente: apagado ate o tick 23");
    led.tick(); // tick 24
    TEST_ASSERT(pins_are(0, 1, 0), "urgente: reacende no tick 24");

    // Um novo estado recomeca em ritmo normal.
    led.update(CoffeeVisualState::Brewing, true);
    TEST_ASSERT(!led.urgent(), "update desliga o modo urgente");
    std::cout << "[PASS] test_urgent_pulse_is_faster_and_resets_on_update" << std::endl;
    return 0;
}

int test_urgent_does_not_change_other_patterns() {
    MockGpio::reset();
    MockLedc::reset();
    RgbLed led;
    led.init();
    led.update(CoffeeVisualState::Ready, true); // branco fixo
    led.set_urgent(true);
    for (int i = 0; i < 60; ++i) led.tick();
    TEST_ASSERT(pins_are(1, 1, 1), "branco fixo nao muda com urgente");
    std::cout << "[PASS] test_urgent_does_not_change_other_patterns" << std::endl;
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
    if (test_breathe_ramps_smoothly_without_flicker()) return 1;
    if (test_common_anode_inverts_levels()) return 1;
    if (test_urgent_pulse_is_faster_and_resets_on_update()) return 1;
    if (test_urgent_does_not_change_other_patterns()) return 1;
    std::cout << "All RgbLed driver tests PASSED!" << std::endl;
    return 0;
}
