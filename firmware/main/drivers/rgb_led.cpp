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
    pattern_ = LedPattern::Solid;
    lit_ = true;
    tick_counter_ = 0;
    write(LedColor::Off);
    return ESP_OK;
}

LedColor RgbLed::color_for(CoffeeVisualState state, bool network_ok) {
    if (state == CoffeeVisualState::Error) {
        return LedColor::Red;
    }
    if (state == CoffeeVisualState::Brewing) {
        return LedColor::Green;
    }
    if (state == CoffeeVisualState::Ready) {
        return LedColor::White;
    }
    if (state == CoffeeVisualState::ScheduleArmed) {
        // Verde fixo se conectado, Âmbar fixo se desconectado (spec §5.3)
        return network_ok ? LedColor::Green : LedColor::Amber;
    }
    // CoffeeVisualState::Idle: Azul respirando se conectado, Amarelo piscando se sem Wi-Fi
    return network_ok ? LedColor::Blue : LedColor::Amber;
}

LedPattern RgbLed::pattern_for(CoffeeVisualState state, bool network_ok) {
    if (state == CoffeeVisualState::Error) {
        return LedPattern::Solid;
    }
    if (state == CoffeeVisualState::Brewing) {
        return LedPattern::Pulse; // Verde pulsando
    }
    if (state == CoffeeVisualState::Ready) {
        return LedPattern::Solid; // Branco fixo
    }
    if (state == CoffeeVisualState::ScheduleArmed) {
        // Verde fixo se conectado, Âmbar fixo se desconectado
        return LedPattern::Solid;
    }
    // CoffeeVisualState::Idle: Azul respirando se conectado, Amarelo piscando se desconectado
    return network_ok ? LedPattern::Breathe : LedPattern::Blink;
}

void RgbLed::update(CoffeeVisualState state, bool network_ok) {
    visual_state_ = state;
    network_ok_ = network_ok;
    color_ = color_for(state, network_ok);
    pattern_ = pattern_for(state, network_ok);
    lit_ = true;
    tick_counter_ = 0;
    write(color_);
}

void RgbLed::tick() {
    if (!initialized_) return;

    ++tick_counter_;

    switch (pattern_) {
        case LedPattern::Solid: {
            if (!lit_) {
                lit_ = true;
                write(color_);
            }
            break;
        }
        case LedPattern::Blink: {
            // Amarelo piscando (1 Hz: 500 ms aceso, 500 ms apagado com tick de 10 ms = 50 ticks)
            const bool should_be_lit = (tick_counter_ % 100) < 50;
            if (lit_ != should_be_lit) {
                lit_ = should_be_lit;
                write(lit_ ? color_ : LedColor::Off);
            }
            break;
        }
        case LedPattern::Pulse: {
            // Verde pulsando (2 Hz: 250 ms aceso, 250 ms apagado = 25 ticks)
            const bool should_be_lit = (tick_counter_ % 50) < 25;
            if (lit_ != should_be_lit) {
                lit_ = should_be_lit;
                write(lit_ ? color_ : LedColor::Off);
            }
            break;
        }
        case LedPattern::Breathe: {
            // Azul respirando: ciclo de 2,4 s (240 ticks de 10 ms)
            // Software PWM a 100 Hz (janela de 10 ticks = 100 ms)
            const uint16_t cycle_tick = tick_counter_ % 240;
            const uint16_t intensity = (cycle_tick < 120) ? cycle_tick : (240 - cycle_tick); // 0 a 120
            const uint16_t duty = 1 + (intensity * 9) / 120; // 1 a 10
            const uint16_t pwm_slot = (tick_counter_ % 10);
            const bool should_be_lit = pwm_slot < duty;
            if (lit_ != should_be_lit) {
                lit_ = should_be_lit;
                write(lit_ ? color_ : LedColor::Off);
            }
            break;
        }
    }
}

void RgbLed::refresh() {
    if (pattern_ == LedPattern::Solid) {
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
