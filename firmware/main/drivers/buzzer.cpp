#include "drivers/buzzer.hpp"

#include <cstddef>

namespace cafey::drivers {

namespace {
constexpr ledc_mode_t kMode = LEDC_LOW_SPEED_MODE;
// 10 bits com o relogio de 80 MHz cobre de ~80 Hz a ~78 kHz: sobra para a
// faixa audivel usada nos padroes (500 Hz a 3 kHz).
constexpr ledc_timer_bit_t kResolution = LEDC_TIMER_10_BIT;
// Duty de 50 %: onda quadrada simetrica, volume maximo do buzzer passivo.
constexpr uint32_t kToneDuty = 1u << (static_cast<uint32_t>(kResolution) - 1);
// Frequencia so para configurar o timer; o canal nasce com duty 0 (mudo).
constexpr uint32_t kInitFreqHz = 2000;

// Notas usadas (Hz). Buzzers passivos magneticos ressoam perto de 2-2,7 kHz:
// as notas mais altas soam mais forte, as graves ficam para o erro.
constexpr uint16_t kG5 = 784;
constexpr uint16_t kC6 = 1047;
constexpr uint16_t kE6 = 1319;
constexpr uint16_t kG6 = 1568;
constexpr uint16_t kC7 = 2093;
constexpr uint16_t kBeep = 2400;
constexpr uint16_t kLow = 660;
constexpr uint16_t kLower = 520;

constexpr Note kBeepNotes[] = {{kBeep, 80}};
constexpr Note kDoubleBeepNotes[] = {{kBeep, 70}, {0, 70}, {kBeep, 70}};
constexpr Note kStartupNotes[] = {{kC6, 80}, {kE6, 80}, {kG6, 140}};
constexpr Note kConnectedNotes[] = {{kE6, 70}, {kG6, 120}};
constexpr Note kDisconnectedNotes[] = {{kG6, 90}, {kC6, 200}};
constexpr Note kBindingNotes[] = {{kC7, 60}, {0, 60}, {kC7, 60}, {0, 60}, {kC7, 60}};
constexpr Note kCancelNotes[] = {{kE6, 70}, {0, 30}, {kG5, 120}};
constexpr Note kReadyNotes[] = {
    {kG5, 140}, {kC6, 140}, {kE6, 140}, {kG6, 260}, {0, 120}, {kE6, 120}, {kG6, 360},
};
constexpr Note kErrorNotes[] = {
    {kLow, 220}, {kLower, 220}, {kLow, 220}, {kLower, 420},
};

template <size_t N>
constexpr uint8_t count_of(const Note (&)[N]) {
    static_assert(N <= 255, "padrao longo demais");
    return static_cast<uint8_t>(N);
}
} // namespace

Buzzer::Buzzer(gpio_num_t pin, ledc_timer_t timer, ledc_channel_t channel)
    : pin_(pin), timer_(timer), channel_(channel) {}

Buzzer::~Buzzer() {
    if (initialized_) silence();
}

esp_err_t Buzzer::init() {
    const ledc_timer_config_t timer_cfg = {
        .speed_mode = kMode,
        .duty_resolution = kResolution,
        .timer_num = timer_,
        .freq_hz = kInitFreqHz,
        .clk_cfg = LEDC_AUTO_CLK,
        .deconfigure = false,
    };
    esp_err_t err = ledc_timer_config(&timer_cfg);
    if (err != ESP_OK) {
        return err;
    }

    const ledc_channel_config_t channel_cfg = {
        .gpio_num = pin_,
        .speed_mode = kMode,
        .channel = channel_,
        .intr_type = LEDC_INTR_DISABLE,
        .timer_sel = timer_,
        .duty = 0,
        .hpoint = 0,
        .flags = {.output_invert = 0},
    };
    err = ledc_channel_config(&channel_cfg);
    if (err != ESP_OK) {
        return err;
    }

    initialized_ = true;
    pattern_ = SoundPattern::None;
    silence();
    return ESP_OK;
}

SoundPattern Buzzer::pattern_for(SoundEvent event) {
    switch (event) {
        case SoundEvent::Boot:             return SoundPattern::Startup;
        case SoundEvent::NetworkOnline:    return SoundPattern::Connected;
        case SoundEvent::NetworkLost:      return SoundPattern::Disconnected;
        case SoundEvent::BindWindowOpened: return SoundPattern::Binding;
        case SoundEvent::BrewStarted:      return SoundPattern::Beep;
        case SoundEvent::BrewCancelled:    return SoundPattern::Cancel;
        case SoundEvent::CoffeeReady:      return SoundPattern::Ready;
        case SoundEvent::Error:            return SoundPattern::Error;
        case SoundEvent::SoundEnabled:     return SoundPattern::DoubleBeep;
    }
    return SoundPattern::None;
}

uint8_t Buzzer::priority(SoundPattern pattern) {
    switch (pattern) {
        case SoundPattern::None:
        case SoundPattern::Connected:
        case SoundPattern::Disconnected:
            return 0;
        case SoundPattern::Beep:
        case SoundPattern::DoubleBeep:
        case SoundPattern::Startup:
        case SoundPattern::Cancel:
            return 1;
        case SoundPattern::Ready:
        case SoundPattern::Binding:
            return 2;
        case SoundPattern::Error:
            return 3;
    }
    return 0;
}

Buzzer::Melody Buzzer::melody_for(SoundPattern pattern) {
    switch (pattern) {
        case SoundPattern::None:         return {nullptr, 0};
        case SoundPattern::Beep:         return {kBeepNotes, count_of(kBeepNotes)};
        case SoundPattern::DoubleBeep:   return {kDoubleBeepNotes, count_of(kDoubleBeepNotes)};
        case SoundPattern::Startup:      return {kStartupNotes, count_of(kStartupNotes)};
        case SoundPattern::Connected:    return {kConnectedNotes, count_of(kConnectedNotes)};
        case SoundPattern::Disconnected: return {kDisconnectedNotes, count_of(kDisconnectedNotes)};
        case SoundPattern::Binding:      return {kBindingNotes, count_of(kBindingNotes)};
        case SoundPattern::Cancel:       return {kCancelNotes, count_of(kCancelNotes)};
        case SoundPattern::Ready:        return {kReadyNotes, count_of(kReadyNotes)};
        case SoundPattern::Error:        return {kErrorNotes, count_of(kErrorNotes)};
    }
    return {nullptr, 0};
}

uint32_t Buzzer::ticks_for(const Note& note) {
    // Arredonda para cima: nenhuma nota dura menos que um tick.
    const uint32_t ticks = (note.duration_ms + kTickMs - 1) / kTickMs;
    return ticks == 0 ? 1 : ticks;
}

uint32_t Buzzer::duration_ticks(SoundPattern pattern) {
    const Melody m = melody_for(pattern);
    uint32_t total = 0;
    for (uint8_t i = 0; i < m.count; ++i) total += ticks_for(m.notes[i]);
    return total;
}

void Buzzer::play(SoundPattern pattern) {
    if (!initialized_ || muted_) return;
    if (playing() && priority(pattern) < priority(pattern_)) return;
    const Melody m = melody_for(pattern);
    if (m.count == 0) {
        stop();
        return;
    }
    pattern_ = pattern;
    melody_ = m;
    start_note(0);
}

void Buzzer::stop() {
    pattern_ = SoundPattern::None;
    melody_ = {nullptr, 0};
    note_index_ = 0;
    note_ticks_left_ = 0;
    if (initialized_) silence();
}

void Buzzer::tick() {
    if (pattern_ == SoundPattern::None) return;
    if (note_ticks_left_ > 0 && --note_ticks_left_ > 0) return;
    const uint8_t next = static_cast<uint8_t>(note_index_ + 1);
    if (next >= melody_.count) {
        stop();
        return;
    }
    start_note(next);
}

void Buzzer::set_muted(bool muted) {
    muted_ = muted;
    if (muted_) stop();
}

void Buzzer::start_note(uint8_t index) {
    note_index_ = index;
    const Note& note = melody_.notes[index];
    note_ticks_left_ = ticks_for(note);
    if (note.freq_hz == 0) {
        silence();
    } else {
        tone(note.freq_hz);
    }
}

void Buzzer::tone(uint16_t freq_hz) {
    if (ledc_set_freq(kMode, timer_, freq_hz) != ESP_OK) {
        silence();
        return;
    }
    ledc_set_duty(kMode, channel_, kToneDuty);
    ledc_update_duty(kMode, channel_);
    current_freq_hz_ = freq_hz;
}

void Buzzer::silence() {
    ledc_set_duty(kMode, channel_, 0);
    ledc_update_duty(kMode, channel_);
    current_freq_hz_ = 0;
}

} // namespace cafey::drivers
