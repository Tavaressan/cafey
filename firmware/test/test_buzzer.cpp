#include <iostream>

#include "drivers/buzzer.hpp"

#define TEST_ASSERT(cond, msg)                                                  \
    do {                                                                       \
        if (!(cond)) {                                                         \
            std::cerr << "FAILED: " << msg << " (" << __FILE__ << ":"          \
                      << __LINE__ << ")" << std::endl;                         \
            return 1;                                                          \
        }                                                                      \
    } while (0)

using cafey::drivers::Buzzer;
using cafey::drivers::SoundEvent;
using cafey::drivers::SoundPattern;

namespace {
constexpr ledc_timer_t kTimer = LEDC_TIMER_1;
constexpr ledc_channel_t kChannel = LEDC_CHANNEL_1;
constexpr uint32_t kHalfDuty = 512; // 50 % em 10 bits

bool sounding() { return MockLedc::get_duty(kChannel) == kHalfDuty; }
bool silent() { return MockLedc::get_duty(kChannel) == 0; }

void reset() {
    MockGpio::reset();
    MockLedc::reset();
}
} // namespace

int test_init_uses_own_timer_and_channel_and_starts_silent() {
    reset();
    Buzzer bz;
    TEST_ASSERT(bz.init() == ESP_OK, "init deve retornar ESP_OK");
    TEST_ASSERT(MockLedc::get_pin(kChannel) == GPIO_NUM_25, "canal 1 no GPIO25");
    TEST_ASSERT(MockLedc::get_pin(LEDC_CHANNEL_0) == -1, "nao toca no canal 0 (LED azul)");
    TEST_ASSERT(MockLedc::get_freq(kTimer) > 0, "timer 1 configurado");
    TEST_ASSERT(MockLedc::get_freq(LEDC_TIMER_0) == 0, "nao reconfigura o timer 0 (LED)");
    TEST_ASSERT(silent() && !bz.playing(), "inicia em silencio");
    std::cout << "[PASS] test_init_uses_own_timer_and_channel_and_starts_silent" << std::endl;
    return 0;
}

int test_play_before_init_is_ignored() {
    reset();
    Buzzer bz;
    bz.play(SoundPattern::Beep);
    TEST_ASSERT(!bz.playing(), "sem init nao toca");
    TEST_ASSERT(MockLedc::duty_by_channel.empty(), "sem init nao escreve no LEDC");
    std::cout << "[PASS] test_play_before_init_is_ignored" << std::endl;
    return 0;
}

int test_beep_sounds_for_its_duration_then_stops() {
    reset();
    Buzzer bz;
    bz.init();
    bz.play(SoundPattern::Beep); // 80 ms = 8 ticks
    TEST_ASSERT(bz.playing() && sounding(), "beep comeca na hora, sem esperar tick");
    TEST_ASSERT(MockLedc::get_freq(kTimer) == bz.current_freq_hz(), "frequencia no timer 1");
    TEST_ASSERT(Buzzer::duration_ticks(SoundPattern::Beep) == 8, "beep dura 8 ticks");
    for (int i = 0; i < 7; ++i) bz.tick();
    TEST_ASSERT(bz.playing() && sounding(), "ainda soando no 7o tick");
    bz.tick();
    TEST_ASSERT(!bz.playing() && silent(), "silencia no 8o tick");
    TEST_ASSERT(bz.current_freq_hz() == 0, "frequencia corrente zerada");
    std::cout << "[PASS] test_beep_sounds_for_its_duration_then_stops" << std::endl;
    return 0;
}

int test_double_beep_has_a_silent_gap() {
    reset();
    Buzzer bz;
    bz.init();
    bz.play(SoundPattern::DoubleBeep); // 70 som, 70 pausa, 70 som
    for (int i = 0; i < 7; ++i) bz.tick();
    TEST_ASSERT(bz.playing() && silent(), "pausa entre os bipes fica muda");
    for (int i = 0; i < 7; ++i) bz.tick();
    TEST_ASSERT(sounding(), "segundo bipe");
    for (int i = 0; i < 7; ++i) bz.tick();
    TEST_ASSERT(!bz.playing() && silent(), "fim do padrao");
    std::cout << "[PASS] test_double_beep_has_a_silent_gap" << std::endl;
    return 0;
}

int test_ready_melody_changes_frequency_and_ends() {
    reset();
    Buzzer bz;
    bz.init();
    bz.play(SoundPattern::Ready);
    const uint16_t first = bz.current_freq_hz();
    bool changed = false;
    const uint32_t total = Buzzer::duration_ticks(SoundPattern::Ready);
    for (uint32_t i = 1; i < total; ++i) {
        bz.tick();
        if (bz.current_freq_hz() != 0 && bz.current_freq_hz() != first) changed = true;
        TEST_ASSERT(bz.playing(), "melodia segue ate o ultimo tick");
    }
    bz.tick();
    TEST_ASSERT(changed, "melodia usa mais de uma nota (buzzer passivo)");
    TEST_ASSERT(!bz.playing() && silent(), "melodia termina em silencio");
    std::cout << "[PASS] test_ready_melody_changes_frequency_and_ends" << std::endl;
    return 0;
}

int test_event_table_maps_each_event() {
    TEST_ASSERT(Buzzer::pattern_for(SoundEvent::Boot) == SoundPattern::Startup, "boot");
    TEST_ASSERT(Buzzer::pattern_for(SoundEvent::NetworkOnline) == SoundPattern::Connected, "rede ok");
    TEST_ASSERT(Buzzer::pattern_for(SoundEvent::NetworkLost) == SoundPattern::Disconnected, "rede caiu");
    TEST_ASSERT(Buzzer::pattern_for(SoundEvent::BindWindowOpened) == SoundPattern::Binding, "vinculo");
    TEST_ASSERT(Buzzer::pattern_for(SoundEvent::BrewStarted) == SoundPattern::Beep, "preparo");
    TEST_ASSERT(Buzzer::pattern_for(SoundEvent::BrewCancelled) == SoundPattern::Cancel, "cancelado");
    TEST_ASSERT(Buzzer::pattern_for(SoundEvent::CoffeeReady) == SoundPattern::Ready, "pronto");
    TEST_ASSERT(Buzzer::pattern_for(SoundEvent::Error) == SoundPattern::Error, "erro");
    TEST_ASSERT(Buzzer::pattern_for(SoundEvent::SoundEnabled) == SoundPattern::DoubleBeep, "som ativado");
    // Nenhum padrao passa de 2 s: o aviso nunca vira ruido continuo.
    for (auto p : {SoundPattern::Beep, SoundPattern::DoubleBeep, SoundPattern::Startup,
                   SoundPattern::Connected, SoundPattern::Disconnected, SoundPattern::Binding,
                   SoundPattern::Cancel, SoundPattern::Ready, SoundPattern::Error}) {
        const uint32_t ticks = Buzzer::duration_ticks(p);
        TEST_ASSERT(ticks > 0 && ticks <= 200, "padrao entre 10 ms e 2 s");
    }
    std::cout << "[PASS] test_event_table_maps_each_event" << std::endl;
    return 0;
}

int test_priority_keeps_important_sound() {
    reset();
    Buzzer bz;
    bz.init();
    bz.notify(SoundEvent::CoffeeReady);
    bz.notify(SoundEvent::NetworkLost);
    TEST_ASSERT(bz.pattern() == SoundPattern::Ready, "aviso de rede nao corta cafe pronto");
    bz.notify(SoundEvent::Error);
    TEST_ASSERT(bz.pattern() == SoundPattern::Error, "erro corta cafe pronto");
    bz.stop();
    bz.notify(SoundEvent::NetworkLost);
    bz.notify(SoundEvent::BrewStarted);
    TEST_ASSERT(bz.pattern() == SoundPattern::Beep, "confirmacao corta aviso de rede");
    bz.notify(SoundEvent::BrewCancelled);
    TEST_ASSERT(bz.pattern() == SoundPattern::Cancel, "mesma prioridade: o mais novo vence");
    std::cout << "[PASS] test_priority_keeps_important_sound" << std::endl;
    return 0;
}

int test_mute_cuts_and_blocks_sounds() {
    reset();
    Buzzer bz;
    bz.init();
    bz.play(SoundPattern::Ready);
    bz.set_muted(true);
    TEST_ASSERT(!bz.playing() && silent(), "silenciar corta o som atual");
    bz.notify(SoundEvent::Error);
    TEST_ASSERT(!bz.playing() && silent(), "silenciado ignora novos sons");
    bz.set_muted(false);
    bz.notify(SoundEvent::BrewStarted);
    TEST_ASSERT(bz.playing() && sounding(), "volta a tocar ao ativar o som");
    std::cout << "[PASS] test_mute_cuts_and_blocks_sounds" << std::endl;
    return 0;
}

int test_destructor_silences_output() {
    reset();
    {
        Buzzer bz;
        bz.init();
        bz.play(SoundPattern::Ready);
        TEST_ASSERT(sounding(), "tocando antes de sair do escopo");
    }
    TEST_ASSERT(silent(), "RAII: destrutor silencia a saida");
    std::cout << "[PASS] test_destructor_silences_output" << std::endl;
    return 0;
}

int main() {
    std::cout << "Running Cafey Buzzer Driver Unit Tests (#311)..." << std::endl;
    if (test_init_uses_own_timer_and_channel_and_starts_silent()) return 1;
    if (test_play_before_init_is_ignored()) return 1;
    if (test_beep_sounds_for_its_duration_then_stops()) return 1;
    if (test_double_beep_has_a_silent_gap()) return 1;
    if (test_ready_melody_changes_frequency_and_ends()) return 1;
    if (test_event_table_maps_each_event()) return 1;
    if (test_priority_keeps_important_sound()) return 1;
    if (test_mute_cuts_and_blocks_sounds()) return 1;
    if (test_destructor_silences_output()) return 1;
    std::cout << "All Buzzer driver tests PASSED!" << std::endl;
    return 0;
}
