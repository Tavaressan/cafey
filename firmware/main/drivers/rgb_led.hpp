#pragma once

#include <cstdint>

#if defined(ESP_PLATFORM)
#include "driver/gpio.h"
#include "esp_err.h"
#else
#include "mock_esp_gpio.hpp"
#endif

namespace cafey::drivers {

/**
 * @brief Estado visual da cafeteira que o LED representa (spec §5.3).
 */
enum class CoffeeVisualState : uint8_t {
    Idle,          // conectado e ocioso / ocioso offline
    ScheduleArmed, // agendamento armado
    Brewing,       // preparando cafe agora
    Ready,         // cafe pronto (alguns minutos apos o preparo)
    Error,         // erro ou falha
};

/**
 * @brief Cor exibida pelo LED RGB.
 */
enum class LedColor : uint8_t { Off, Blue, Green, Amber, White, Red };

/**
 * @brief Padrao de animacao do LED (spec §5.3).
 */
enum class LedPattern : uint8_t {
    Solid,    // Fixo (Verde fixo, Âmbar fixo, Branco fixo, Vermelho fixo)
    Blink,    // Piscando (Amarelo piscando quando sem Wi-Fi)
    Pulse,    // Pulsando (Verde pulsando durante preparo)
    Breathe,  // Respirando lento (Azul respirando quando conectado e ocioso)
};

/**
 * @brief Driver do LED RGB (spec §5.3).
 *
 * Regra mental da especificacao: a *cor* indica o estado da cafeteira; o
 * *piscar* indica problema de rede. LED de catodo comum por padrao (HW-01):
 * nivel alto acende o canal. Para anodo comum, `common_anode = true` inverte.
 */
class RgbLed {
public:
    explicit RgbLed(gpio_num_t pin_r = GPIO_NUM_21,
                    gpio_num_t pin_g = GPIO_NUM_22,
                    gpio_num_t pin_b = GPIO_NUM_23,
                    bool common_anode = false);

    /**
     * @brief Configura os tres GPIOs como saida e apaga o LED.
     * @return ESP_OK em sucesso, ou codigo de erro do ESP-IDF.
     */
    esp_err_t init();

    /**
     * @brief Define o estado visual e condicao de rede conforme spec §5.3.
     */
    void update(CoffeeVisualState state, bool network_ok);

    /**
     * @brief Chamada periodica de tick (ex.: a cada 10 ms no ActiveObject).
     * Modula os padroes de animacao (Solid, Blink, Pulse, Breathe).
     */
    void tick();

    /**
     * @brief Chamada para alternar o ciclo de blink/pulsar manualmente (testes/compatibilidade).
     */
    void refresh();

    [[nodiscard]] LedColor color() const noexcept { return color_; }
    [[nodiscard]] LedPattern pattern() const noexcept { return pattern_; }
    [[nodiscard]] bool blinking() const noexcept { return pattern_ == LedPattern::Blink; }
    [[nodiscard]] bool pulsing() const noexcept { return pattern_ == LedPattern::Pulse; }
    [[nodiscard]] bool breathing() const noexcept { return pattern_ == LedPattern::Breathe; }
    [[nodiscard]] bool lit() const noexcept { return lit_; }
    [[nodiscard]] bool is_initialized() const noexcept { return initialized_; }
    [[nodiscard]] CoffeeVisualState visual_state() const noexcept { return visual_state_; }
    [[nodiscard]] bool network_ok() const noexcept { return network_ok_; }

private:
    static LedColor color_for(CoffeeVisualState state, bool network_ok);
    static LedPattern pattern_for(CoffeeVisualState state, bool network_ok);
    void write(LedColor color);

    gpio_num_t pin_r_;
    gpio_num_t pin_g_;
    gpio_num_t pin_b_;
    bool common_anode_;
    bool initialized_ = false;

    CoffeeVisualState visual_state_ = CoffeeVisualState::Idle;
    bool network_ok_ = true;
    LedColor color_ = LedColor::Off;
    LedPattern pattern_ = LedPattern::Solid;
    bool lit_ = true;

    uint16_t tick_counter_ = 0;
};

} // namespace cafey::drivers
