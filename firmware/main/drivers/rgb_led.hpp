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
    Idle,          // conectado e ocioso
    ScheduleArmed, // agendamento armado
    Brewing,       // preparando cafe agora
    Ready,         // cafe pronto
    Error,         // erro ou falha
};

/**
 * @brief Cor exibida pelo LED RGB.
 */
enum class LedColor : uint8_t { Off, Blue, Green, Amber, White, Red };

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
     * @brief Define o estado logico do LED. `network_ok == false` faz o LED
     * piscar (o piscar so tem efeito visual apos chamadas de refresh()).
     */
    void update(CoffeeVisualState state, bool network_ok);

    /**
     * @brief Chamada periodica: alterna aceso/apagado enquanto estiver piscando.
     */
    void refresh();

    [[nodiscard]] LedColor color() const noexcept { return color_; }
    [[nodiscard]] bool blinking() const noexcept { return blinking_; }
    [[nodiscard]] bool lit() const noexcept { return lit_; }
    [[nodiscard]] bool is_initialized() const noexcept { return initialized_; }

private:
    static LedColor color_for(CoffeeVisualState state, bool network_ok);
    void write(LedColor color);

    gpio_num_t pin_r_;
    gpio_num_t pin_g_;
    gpio_num_t pin_b_;
    bool common_anode_;
    bool initialized_ = false;
    LedColor color_ = LedColor::Off;
    bool blinking_ = false;
    bool lit_ = true;
};

} // namespace cafey::drivers
