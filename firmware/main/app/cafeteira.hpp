#pragma once

#include <cstdint>

#include "core/active_object.hpp"
#include "drivers/relay.hpp" // tambem traz o header de GPIO (real ou mock)

namespace cafey::app {

/**
 * @brief Active Object da Cafeteira: maquina de estados do preparo,
 * acionamento do rele, LED RGB e leitura do botao fisico (por polling).
 */
class Cafeteira : public cafey::core::ActiveObject {
public:
    enum class State : uint8_t { Off, On };

    /**
     * @brief Desfecho do ultimo preparo (spec-backend §7.2, coluna `resultado`).
     * `Concluido` = corte pelo temporizador local; `Cancelado` = corte por
     * botao ou comando (UC-09).
     */
    enum class Result : uint8_t { None, Concluido, Cancelado };

    // Default de `duracaoS` quando o comando nao informa (spec-backend P9).
    static constexpr uint32_t kDefaultDurationS = 300;

    explicit Cafeteira(gpio_num_t relay_pin = GPIO_NUM_26,
                       gpio_num_t button_pin = GPIO_NUM_27,
                       gpio_num_t led_r = GPIO_NUM_21,
                       gpio_num_t led_g = GPIO_NUM_22,
                       gpio_num_t led_b = GPIO_NUM_23);

    [[nodiscard]] State state() const noexcept { return state_; }
    [[nodiscard]] bool relay_on() const noexcept { return relay_.is_on(); }
    [[nodiscard]] Result last_result() const noexcept { return last_result_; }
    [[nodiscard]] uint32_t brew_seconds_left() const noexcept {
        return (brew_ticks_left_ + kTicksPerSecond - 1) / kTicksPerSecond;
    }

protected:
    void on_start() override;
    void on_tick() override; // polling do botao com debounce
    void dispatch(const cafey::core::Message& msg) override;

private:
    static constexpr int kDebounceSamples = 3; // 3 x 10 ms = 30 ms estaveis
    // O tick do AO roda a cada 10 ms -> 100 ticks por segundo.
    static constexpr uint32_t kTicksPerSecond = 100;

    void apply_state();
    void led_set(bool r, bool g, bool b);
    void start_brew(uint32_t duration_s);
    void cancel_brew();
    void finish_brew();

    cafey::drivers::Relay relay_;
    gpio_num_t button_pin_;
    gpio_num_t led_r_;
    gpio_num_t led_g_;
    gpio_num_t led_b_;
    State state_ = State::Off;
    uint32_t brew_ticks_left_ = 0; // ticks restantes do preparo em curso
    Result last_result_ = Result::None;
    int btn_reading_ = 1;
    int btn_stable_ = 1;
    int btn_same_count_ = 0;
};

} // namespace cafey::app
