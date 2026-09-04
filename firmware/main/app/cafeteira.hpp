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

    explicit Cafeteira(gpio_num_t relay_pin = GPIO_NUM_26,
                       gpio_num_t button_pin = GPIO_NUM_27,
                       gpio_num_t led_r = GPIO_NUM_21,
                       gpio_num_t led_g = GPIO_NUM_22,
                       gpio_num_t led_b = GPIO_NUM_23);

    [[nodiscard]] State state() const noexcept { return state_; }
    [[nodiscard]] bool relay_on() const noexcept { return relay_.is_on(); }

protected:
    void on_start() override;
    void on_tick() override; // polling do botao com debounce
    void dispatch(const cafey::core::Message& msg) override;

private:
    static constexpr int kDebounceSamples = 3; // 3 x 10 ms = 30 ms estaveis

    void apply_state();
    void led_set(bool r, bool g, bool b);

    cafey::drivers::Relay relay_;
    gpio_num_t button_pin_;
    gpio_num_t led_r_;
    gpio_num_t led_g_;
    gpio_num_t led_b_;
    State state_ = State::Off;
    int btn_reading_ = 1;
    int btn_stable_ = 1;
    int btn_same_count_ = 0;
};

} // namespace cafey::app
