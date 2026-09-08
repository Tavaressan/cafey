#include "app/cafeteira.hpp"

namespace cafey::app {

using cafey::core::Message;
using cafey::core::MessageType;

Cafeteira::Cafeteira(gpio_num_t relay_pin, gpio_num_t button_pin,
                     gpio_num_t led_r, gpio_num_t led_g, gpio_num_t led_b)
    : ActiveObject("cafeteira", 16, 4096, 5, /*tick_period_ms=*/10),
      relay_(relay_pin),
      button_pin_(button_pin),
      led_r_(led_r),
      led_g_(led_g),
      led_b_(led_b) {}

void Cafeteira::led_set(bool r, bool g, bool b) {
    gpio_set_level(led_r_, r ? 1 : 0);
    gpio_set_level(led_g_, g ? 1 : 0);
    gpio_set_level(led_b_, b ? 1 : 0);
}

void Cafeteira::apply_state() {
    if (state_ == State::On) {
        relay_.turn_on();
        led_set(false, true, false); // verde = ligada
    } else {
        relay_.turn_off();
        led_set(true, false, false); // vermelho = desligada
    }
}

void Cafeteira::start_brew(uint32_t duration_s) {
    if (duration_s == 0) duration_s = kDefaultDurationS;
    brew_ticks_left_ = duration_s * kTicksPerSecond;
    last_result_ = Result::None;
    state_ = State::On;
    apply_state();
}

void Cafeteira::cancel_brew() {
    // Corte por botao/comando enquanto preparava -> CANCELADO (UC-09).
    if (state_ == State::On) last_result_ = Result::Cancelado;
    brew_ticks_left_ = 0;
    state_ = State::Off;
    apply_state();
}

void Cafeteira::finish_brew() {
    // Fim do preparo pelo temporizador local. Sem sensor de extracao, o corte
    // de energia da carga tem o mesmo desfecho: resultado CONCLUIDO
    // (spec-backend §14 / P7).
    brew_ticks_left_ = 0;
    last_result_ = Result::Concluido;
    state_ = State::Off;
    apply_state();
}

void Cafeteira::on_start() {
    relay_.init();

    const gpio_config_t led_cfg = {
        .pin_bit_mask = (1ULL << led_r_) | (1ULL << led_g_) | (1ULL << led_b_),
        .mode = GPIO_MODE_OUTPUT,
        .pull_up_en = GPIO_PULLUP_DISABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type = GPIO_INTR_DISABLE,
    };
    gpio_config(&led_cfg);

    const gpio_config_t btn_cfg = {
        .pin_bit_mask = (1ULL << button_pin_),
        .mode = GPIO_MODE_INPUT,
        .pull_up_en = GPIO_PULLUP_ENABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type = GPIO_INTR_DISABLE,
    };
    gpio_config(&btn_cfg);

    btn_reading_ = gpio_get_level(button_pin_);
    btn_stable_ = btn_reading_;
    btn_same_count_ = 0;

    state_ = State::Off;
    apply_state();
}

void Cafeteira::on_tick() {
    int level = gpio_get_level(button_pin_);
    if (level != btn_reading_) {
        btn_reading_ = level;
        btn_same_count_ = 0;
        return;
    }
    if (btn_same_count_ >= kDebounceSamples) {
        // Temporizador local do preparo: decrementa a cada tick sem botao.
        if (state_ == State::On && brew_ticks_left_ > 0 && --brew_ticks_left_ == 0) {
            finish_brew();
        }
        return;
    }
    if (++btn_same_count_ == kDebounceSamples && level != btn_stable_) {
        btn_stable_ = level;
        if (btn_stable_ == 0) { // borda de descida (pull-up): clique
            post(Message{MessageType::ButtonPressed, 0});
        }
    }
}

void Cafeteira::dispatch(const Message& msg) {
    switch (msg.type) {
        case MessageType::ButtonPressed:
            // Toque curto: liga (inicia preparo) / desliga (cancela).
            if (state_ == State::Off) {
                start_brew(kDefaultDurationS);
            } else {
                cancel_brew();
            }
            break;
        case MessageType::StartBrew:
            // msg.arg = duracaoS enviado pelo comando/agendamento.
            start_brew(msg.arg > 0 ? static_cast<uint32_t>(msg.arg) : kDefaultDurationS);
            break;
        case MessageType::StopBrew:
            cancel_brew();
            break;
        case MessageType::BrewFinished:
            finish_brew();
            break;
        default:
            break;
    }
}

} // namespace cafey::app
