#include "drivers/rgb_led.hpp"

namespace cafey::drivers {

RgbLed::RgbLed(gpio_num_t pin_r, gpio_num_t pin_g, gpio_num_t pin_b,
               bool common_anode)
    : pin_r_(pin_r), pin_g_(pin_g), pin_b_(pin_b), common_anode_(common_anode) {}

esp_err_t RgbLed::init() {
    const gpio_config_t cfg = {
        .pin_bit_mask = (1ULL << pin_r_) | (1ULL << pin_g_) | (1ULL << pin_b_),
        .mode = GPIO_MODE_OUTPUT,
        .pull_up_en = GPIO_PULLUP_DISABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type = GPIO_INTR_DISABLE,
    };
    esp_err_t err = gpio_config(&cfg);
    if (err != ESP_OK) {
        return err;
    }
    initialized_ = true;
    color_ = LedColor::Off;
    blinking_ = false;
    lit_ = true;
    write(LedColor::Off);
    return ESP_OK;
}

LedColor RgbLed::color_for(CoffeeVisualState state, bool network_ok) {
    if (state == CoffeeVisualState::Error) {
        return LedColor::Red;
    }
    if (!network_ok) {
        // Sem rede: ocioso ou com agendamento armado -> ambar (spec §5.3).
        if (state == CoffeeVisualState::Idle ||
            state == CoffeeVisualState::ScheduleArmed) {
            return LedColor::Amber;
        }
    }
    switch (state) {
        case CoffeeVisualState::Idle:
            return LedColor::Blue;
        case CoffeeVisualState::ScheduleArmed:
        case CoffeeVisualState::Brewing:
            return LedColor::Green;
        case CoffeeVisualState::Ready:
            return LedColor::White;
        default:
            return LedColor::Off;
    }
}

void RgbLed::update(CoffeeVisualState state, bool network_ok) {
    color_ = color_for(state, network_ok);
    // Piscar sinaliza problema de rede; o estado de erro fica fixo em vermelho.
    blinking_ = !network_ok && state != CoffeeVisualState::Error;
    lit_ = true;
    write(color_);
}

void RgbLed::refresh() {
    if (!blinking_) {
        if (!lit_) {
            lit_ = true;
            write(color_);
        }
        return;
    }
    lit_ = !lit_;
    write(lit_ ? color_ : LedColor::Off);
}

void RgbLed::write(LedColor color) {
    bool r = false, g = false, b = false;
    switch (color) {
        case LedColor::Off:                             break;
        case LedColor::Blue:  b = true;                 break;
        case LedColor::Green: g = true;                 break;
        case LedColor::Red:   r = true;                 break;
        case LedColor::Amber: r = true; g = true;       break;
        case LedColor::White: r = true; g = true; b = true; break;
    }
    if (common_anode_) { // anodo comum: nivel baixo acende
        r = !r;
        g = !g;
        b = !b;
    }
    gpio_set_level(pin_r_, r ? 1 : 0);
    gpio_set_level(pin_g_, g ? 1 : 0);
    gpio_set_level(pin_b_, b ? 1 : 0);
}

} // namespace cafey::drivers
