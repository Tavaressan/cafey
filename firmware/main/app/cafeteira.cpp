#include "app/cafeteira.hpp"

namespace cafey::app {

using cafey::core::Message;
using cafey::core::MessageType;

namespace {
constexpr const char* TAG = "cafeteira";
} // namespace

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
    if (on_state_change_) on_state_change_(state_ == State::On);
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
        default:
            break;
    }
}

} // namespace cafey::app
