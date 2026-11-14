#include "app/cafeteira.hpp"

#include <cstdio>
#include <cstdlib>

#if defined(ESP_PLATFORM)
#include "esp_random.h"
#endif

namespace cafey::app {

using cafey::core::Message;
using cafey::core::MessageType;

namespace {
constexpr const char* TAG = "cafeteira";
} // namespace

Cafeteira::Cafeteira(gpio_num_t relay_pin, gpio_num_t button_pin,
                     gpio_num_t led_r, gpio_num_t led_g, gpio_num_t led_b)
    : ActiveObject("cafeteira", 16, 4096, 5, /*tick_period_ms=*/10),
      relay_(relay_pin, /*auto_init=*/false, kRelayActiveLevel),
      led_(led_r, led_g, led_b),
      button_pin_(button_pin) {}

void Cafeteira::set_network_ok(bool ok) {
    network_ok_ = ok;
    led_.update(visual_state_, network_ok_);
}

void Cafeteira::set_schedules_armed(bool armed) {
    has_armed_schedules_ = armed;
    apply_state();
}

void Cafeteira::set_error(bool error) {
    has_error_ = error;
    apply_state();
}

void Cafeteira::apply_state() {
    if (state_ == State::On) {
        relay_.turn_on();
        ready_ticks_left_ = 0;
        visual_state_ = drivers::CoffeeVisualState::Brewing;
    } else {
        relay_.turn_off();
        if (bind_ticks_left_ > 0) {
            // Janela de vinculo aberta: azul piscando (FW-24, #275).
            visual_state_ = drivers::CoffeeVisualState::Binding;
        } else if (ready_ticks_left_ > 0) {
            visual_state_ = drivers::CoffeeVisualState::Ready;
        } else if (has_error_) {
            visual_state_ = drivers::CoffeeVisualState::Error;
        } else if (has_armed_schedules_) {
            visual_state_ = drivers::CoffeeVisualState::ScheduleArmed;
        } else {
            visual_state_ = drivers::CoffeeVisualState::Idle;
        }
    }
    led_.update(visual_state_, network_ok_);
    if (on_state_change_) on_state_change_(state_ == State::On);
}

std::string Cafeteira::generate_bind_code() {
#if defined(ESP_PLATFORM)
    const uint32_t value = esp_random();
#else
    const uint32_t value = static_cast<uint32_t>(std::rand());
#endif
    char buf[7];
    std::snprintf(buf, sizeof(buf), "%06u", static_cast<unsigned>(value % 1000000u));
    return std::string(buf);
}

void Cafeteira::open_bind_window() {
    bind_code_ = generate_bind_code();
    bind_ticks_left_ = kBindWindowS * kTicksPerSecond;
    ESP_LOGI(TAG, "janela de vinculo aberta (%us)", static_cast<unsigned>(kBindWindowS));
    apply_state();
    if (on_bind_window_change_) on_bind_window_change_(true, bind_code_);
}

void Cafeteira::close_bind_window() {
    bind_ticks_left_ = 0;
    bind_code_.clear();
    ESP_LOGI(TAG, "janela de vinculo encerrada");
    apply_state();
    if (on_bind_window_change_) on_bind_window_change_(false, std::string());
}

void Cafeteira::start_brew(uint32_t duration_s, cafey::storage::EventOrigin origin) {
    if (duration_s == 0) duration_s = kDefaultDurationS;
    // Teto de seguranca independente do backend (issue #143): cobre o comando
    // MQTT, o fallback BLE (spec §6.4) e o disparo de agendamento local, que
    // convergem todos para este ponto unico antes de acionar o rele.
    if (duration_s > kMaxDurationS) {
        ESP_LOGW(TAG, "duracaoS %u acima do teto; clampando para %u s",
                 static_cast<unsigned>(duration_s), static_cast<unsigned>(kMaxDurationS));
        duration_s = kMaxDurationS;
    }
    brew_ticks_total_ = duration_s * kTicksPerSecond;
    brew_ticks_left_ = brew_ticks_total_;
    ready_ticks_left_ = 0;
    current_origin_ = origin;
    brew_start_epoch_ = clock_ ? static_cast<uint32_t>(clock_->now()) : 0;
    last_result_ = Result::None;
    state_ = State::On;
    apply_state();
}

void Cafeteira::enqueue_brew_event(cafey::storage::EventResult result, uint32_t duration_s) {
    if (event_queue_ == nullptr || event_id_gen_ == nullptr) return;

    char id_buf[cafey::storage::EventIdGenerator::kMaxIdLen];
    esp_err_t err = event_id_gen_->next(id_buf, sizeof(id_buf));
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "falha ao gerar evento_id: %d", err);
        return;
    }

    const uint32_t now_epoch = clock_ ? static_cast<uint32_t>(clock_->now()) : 0;
    const uint32_t start_epoch = brew_start_epoch_ != 0 ? brew_start_epoch_ : now_epoch;
    const uint32_t end_epoch = now_epoch >= start_epoch ? now_epoch : (start_epoch + duration_s);

    cafey::storage::Event evt = cafey::storage::make_brew_event(
        id_buf, current_origin_, result, duration_s,
        start_epoch, end_epoch, ntp_synced_);

    err = event_queue_->push(evt);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "falha ao enfileirar evento de preparo: %d", err);
        return;
    }

    if (on_event_queued_) on_event_queued_();
}

void Cafeteira::cancel_brew() {
    // Corte por botao/comando enquanto preparava -> CANCELADO (UC-09).
    if (state_ == State::On) {
        last_result_ = Result::Cancelado;
        const uint32_t elapsed_ticks = (brew_ticks_total_ > brew_ticks_left_)
                                           ? (brew_ticks_total_ - brew_ticks_left_)
                                           : 0;
        const uint32_t elapsed_s = elapsed_ticks / kTicksPerSecond;
        enqueue_brew_event(cafey::storage::EventResult::CANCELADO, elapsed_s);
    }
    brew_ticks_left_ = 0;
    brew_ticks_total_ = 0;
    ready_ticks_left_ = 0;
    state_ = State::Off;
    apply_state();
}

void Cafeteira::finish_brew() {
    // Fim do preparo pelo temporizador local. Sem sensor de extracao, o corte
    // de energia da carga tem o mesmo desfecho: resultado CONCLUIDO
    // (spec-backend §14 / P7).
    const uint32_t duracao_s = brew_ticks_total_ / kTicksPerSecond;
    brew_ticks_left_ = 0;
    brew_ticks_total_ = 0;
    last_result_ = Result::Concluido;
    enqueue_brew_event(cafey::storage::EventResult::CONCLUIDO, duracao_s);
    state_ = State::Off;
    // Spec §5.3: Branco fixo (alguns minutos) | Café pronto
    ready_ticks_left_ = kReadyDurationS * kTicksPerSecond;
    apply_state();
}

void Cafeteira::on_start() {
    relay_.init();
    led_.init();

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
    btn_press_ticks_ = 0;
    long_press_consumed_ = false;
    bind_ticks_left_ = 0;
    bind_code_.clear();

    state_ = State::Off;
    apply_state();
}

void Cafeteira::on_tick() {
    // Janela de vinculo (FW-24, #275): conta regressiva em ticks e fecha no fim.
    if (bind_ticks_left_ > 0 && --bind_ticks_left_ == 0) {
        close_bind_window();
    }

    // Atualiza o padrao visual do LED (respira/pisca/pulsa/fixo)
    led_.tick();

    // Temporizador de "Cafe pronto" (Branco fixo por alguns minutos)
    if (ready_ticks_left_ > 0 && state_ == State::Off) {
        if (--ready_ticks_left_ == 0) {
            apply_state();
        }
    }

    // Temporizador local do preparo: decrementa a cada tick enquanto o botao esta estavel.
    if (btn_same_count_ >= kDebounceSamples &&
        state_ == State::On && brew_ticks_left_ > 0 && --brew_ticks_left_ == 0
    ) {
        finish_brew();
    }

    // Reta final: LED pulsa mais rapido nos ultimos kFinalStretchS segundos.
    led_.set_urgent(state_ == State::On && brew_ticks_left_ > 0 &&
                    brew_ticks_total_ >= kFinalStretchMinDurationS * kTicksPerSecond &&
                    brew_seconds_left() <= kFinalStretchS);

    const int level = gpio_get_level(button_pin_);
    if (level != btn_reading_) {
        btn_reading_ = level;
        btn_same_count_ = 0;
        return;
    }
    if (btn_same_count_ < kDebounceSamples) ++btn_same_count_;
    if (btn_same_count_ == kDebounceSamples && level != btn_stable_) {
        btn_stable_ = level;
        if (btn_stable_ == 0) { // borda de descida (pull-up): inicio do toque
            btn_press_ticks_ = 0;
            long_press_consumed_ = false;
        } else if (!long_press_consumed_) {
            // Toque curto: liga (inicia preparo) / desliga (cancela) na soltura,
            // para que segurar 5 s nao acione o preparo antes de abrir a janela.
            post(Message{MessageType::ButtonPressed, 0});
        }
    }

    // Toque longo: abre a janela ainda com o botao pressionado.
    if (btn_same_count_ >= kDebounceSamples && btn_stable_ == 0) {
        if (btn_press_ticks_ < kLongPressTicks) ++btn_press_ticks_;
        if (!long_press_consumed_ && btn_press_ticks_ >= kLongPressTicks) {
            long_press_consumed_ = true;
            open_bind_window();
        }
    }
}

void Cafeteira::dispatch(const Message& msg) {
    switch (msg.type) {
        case MessageType::ButtonPressed:
            if (ready_ticks_left_ > 0) {
                // Clique durante o aviso de pronto: dispensa o estado pronto
                ready_ticks_left_ = 0;
                apply_state();
                break;
            }
            // Toque curto: liga (inicia preparo) / desliga (cancela).
            if (state_ == State::Off) {
                start_brew(kDefaultDurationS, cafey::storage::EventOrigin::BOTAO);
            } else {
                cancel_brew();
            }
            break;
        case MessageType::StartBrew: {
            // msg.arg = duracaoS enviado pelo comando/agendamento.
            const uint32_t dur = msg.arg > 0 ? static_cast<uint32_t>(msg.arg) : kDefaultDurationS;
            cafey::storage::EventOrigin origin = cafey::storage::EventOrigin::APP;
            if (msg.arg2 == static_cast<int32_t>(cafey::storage::EventOrigin::AGENDAMENTO)) {
                origin = cafey::storage::EventOrigin::AGENDAMENTO;
            } else if (msg.arg2 == static_cast<int32_t>(cafey::storage::EventOrigin::BOTAO)) {
                origin = cafey::storage::EventOrigin::BOTAO;
            }
            start_brew(dur, origin);
            break;
        }
        case MessageType::StopBrew:
            cancel_brew();
            break;
        case MessageType::BrewFinished:
            finish_brew();
            break;
        case MessageType::TimeSynced:
            ntp_synced_ = true;
            break;
        case MessageType::NetworkStatusChanged:
            set_network_ok(msg.arg != 0);
            break;
        case MessageType::ScheduleArmedChanged:
            set_schedules_armed(msg.arg != 0);
            break;
        default:
            break;
    }
}

} // namespace cafey::app
