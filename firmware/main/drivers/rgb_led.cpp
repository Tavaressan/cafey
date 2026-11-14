#include "drivers/rgb_led.hpp"

namespace cafey::drivers {

namespace {
// Canal azul via LEDC (PWM de hardware). 5 kHz com resolucao de 8 bits fica
// bem acima da faixa de cintilacao perceptivel.
constexpr ledc_mode_t kBlueMode = LEDC_LOW_SPEED_MODE;
constexpr ledc_timer_t kBlueTimer = LEDC_TIMER_0;
constexpr ledc_channel_t kBlueChannel = LEDC_CHANNEL_0;
constexpr uint32_t kBlueFreqHz = 5000;
constexpr uint32_t kMaxBrightness = 255; // 8 bits

// Respiracao: ciclo de 2,4 s = 240 ticks de 10 ms (subida de 120 e descida de 120).
constexpr uint16_t kBreatheTicks = 240;
constexpr uint16_t kBreatheHalf = 120;

// Wrap do contador de ticks: minimo multiplo comum de 100 (blink), 50 (pulse)
// e 240 (breathe). Mantem todos os padroes continuos ao dar a volta.
constexpr uint16_t kCounterWrap = 1200;

// Pulse: ciclo de 50 ticks (2 Hz) no normal e de 25 ticks (4 Hz) no urgente. Os dois
// dividem kCounterWrap, entao o padrao continua sem emenda ao dar a volta.
constexpr uint16_t kPulseTicks = 50;
constexpr uint16_t kPulseTicksUrgent = 24; // 4 Hz aproximado; 1200 / 24 = 50 ciclos exatos
} // namespace

RgbLed::RgbLed(gpio_num_t pin_r, gpio_num_t pin_g, gpio_num_t pin_b,
               bool common_anode)
    : pin_r_(pin_r), pin_g_(pin_g), pin_b_(pin_b), common_anode_(common_anode) {}

esp_err_t RgbLed::init() {
    const gpio_config_t cfg = {
        .pin_bit_mask = (1ULL << pin_r_) | (1ULL << pin_g_),
        .mode = GPIO_MODE_OUTPUT,
        .pull_up_en = GPIO_PULLUP_DISABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type = GPIO_INTR_DISABLE,
    };
    esp_err_t err = gpio_config(&cfg);
    if (err != ESP_OK) {
        return err;
    }

    const ledc_timer_config_t timer_cfg = {
        .speed_mode = kBlueMode,
        .duty_resolution = LEDC_TIMER_8_BIT,
        .timer_num = kBlueTimer,
        .freq_hz = kBlueFreqHz,
        .clk_cfg = LEDC_AUTO_CLK,
        .deconfigure = false,
    };
    err = ledc_timer_config(&timer_cfg);
    if (err != ESP_OK) {
        return err;
    }

    const ledc_channel_config_t channel_cfg = {
        .gpio_num = pin_b_,
        .speed_mode = kBlueMode,
        .channel = kBlueChannel,
        .intr_type = LEDC_INTR_DISABLE,
        .timer_sel = kBlueTimer,
        .duty = 0,
        .hpoint = 0,
        .flags = {.output_invert = 0},
    };
    err = ledc_channel_config(&channel_cfg);
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
    if (state == CoffeeVisualState::Binding) {
        // Azul piscando: distinto do azul respirando (Idle) e do ambar piscando (sem Wi-Fi).
        return LedColor::Blue;
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
    if (state == CoffeeVisualState::Binding) {
        return LedPattern::Blink; // Azul piscando
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
    urgent_ = false;
    tick_counter_ = 0;
    write(color_);
    if (pattern_ == LedPattern::Breathe) {
        // Comeca apagado para o primeiro tick subir o brilho sem um flash cheio.
        write_blue(0);
        lit_ = false;
    }
}

void RgbLed::tick() {
    if (!initialized_) return;

    tick_counter_ = (tick_counter_ + 1) % kCounterWrap;

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
            // Verde pulsando (2 Hz: 250 ms aceso, 250 ms apagado = 25 ticks); nos
            // ultimos 60 s do preparo pulsa mais rapido (urgente, ~4 Hz).
            const uint16_t period = urgent_ ? kPulseTicksUrgent : kPulseTicks;
            const bool should_be_lit = (tick_counter_ % period) < (period / 2);
            if (lit_ != should_be_lit) {
                lit_ = should_be_lit;
                write(lit_ ? color_ : LedColor::Off);
            }
            break;
        }
        case LedPattern::Breathe: {
            // Azul respirando via PWM de hardware (LEDC): brilho sobe e desce
            // linearmente no tempo, com curva quadratica para parecer uniforme
            // ao olho. Cada tick (10 ms) atualiza o duty; sem cintilacao.
            const uint16_t cycle_tick = tick_counter_ % kBreatheTicks;
            const uint32_t intensity = (cycle_tick < kBreatheHalf)
                                           ? cycle_tick
                                           : (kBreatheTicks - cycle_tick); // 0 a 120
            const uint32_t brightness =
                intensity * intensity * kMaxBrightness / (kBreatheHalf * kBreatheHalf); // 0 a 255
            lit_ = brightness > 0;
            write_blue(brightness);
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
    }
    gpio_set_level(pin_r_, r ? 1 : 0);
    gpio_set_level(pin_g_, g ? 1 : 0);
    write_blue(b ? kMaxBrightness : 0);
}

void RgbLed::write_blue(uint32_t brightness) {
    const uint32_t level = brightness > kMaxBrightness ? kMaxBrightness : brightness;
    // Anodo comum: duty invertido (duty 0 = canal aceso no maximo).
    const uint32_t duty = common_anode_ ? kMaxBrightness - level : level;
    ledc_set_duty(kBlueMode, kBlueChannel, duty);
    ledc_update_duty(kBlueMode, kBlueChannel);
}

} // namespace cafey::drivers
