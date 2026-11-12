#pragma once

#include <cstdint>

#if defined(ESP_PLATFORM)
#include "driver/ledc.h"
#include "esp_err.h"
#else
#include "mock_esp_gpio.hpp"
#include "mock_esp_ledc.hpp"
#endif

namespace cafey::drivers {

/**
 * @brief Evento do sistema que merece aviso sonoro (#311).
 *
 * E o equivalente sonoro de `CoffeeVisualState`: quem dispara o som informa o
 * *evento*, e a tabela `pattern_for` decide o *padrao*.
 */
enum class SoundEvent : uint8_t {
    Boot,             // app_main / Cafeteira::on_start
    NetworkOnline,    // rede (Wi-Fi ou MQTT) voltou
    NetworkLost,      // falha de comunicacao
    BindWindowOpened, // toque longo abriu a janela de vinculo (FW-24)
    BrewStarted,      // preparo iniciado (botao, app ou agendamento)
    BrewCancelled,    // preparo cortado por botao ou comando
    CoffeeReady,      // Cafeteira::finish_brew (resultado CONCLUIDO)
    Error,            // Cafeteira::set_error(true)
    SoundEnabled,     // confirmacao ao sair do modo silencioso
};

/**
 * @brief Padrao sonoro reutilizavel: uma sequencia curta de notas.
 */
enum class SoundPattern : uint8_t {
    None,
    Beep,       // bipe curto
    DoubleBeep, // dois bipes
    Startup,    // arpejo ascendente curto
    Connected,  // duas notas ascendentes
    Disconnected, // duas notas descendentes
    Binding,    // tres bipes agudos
    Cancel,     // duas notas descendentes, mais curtas que Disconnected
    Ready,      // melodia de cafe pronto
    Error,      // alternancia grave, longa
};

/**
 * @brief Nota de um padrao: frequencia em Hz (0 = pausa) e duracao em ms.
 */
struct Note {
    uint16_t freq_hz;
    uint16_t duration_ms;
};

/**
 * @brief Driver do buzzer passivo (#311).
 *
 * O tom sai por PWM de hardware (LEDC), num timer e canal proprios, separados
 * do canal azul do LED (timer 0 / canal 0). O GPIO aciona a base de um
 * transistor NPN; o buzzer fica entre +5 V e o coletor. Duty 0 = transistor
 * cortado = silencio.
 *
 * Nao bloqueia: `play()` so arma a sequencia, e `tick()` (chamado a cada
 * 10 ms pelo Active Object dono) avanca as notas. Nenhum `vTaskDelay`.
 *
 * RAII: o destrutor silencia a saida.
 */
class Buzzer {
public:
    static constexpr uint32_t kTickMs = 10;

    explicit Buzzer(gpio_num_t pin = GPIO_NUM_25,
                    ledc_timer_t timer = LEDC_TIMER_1,
                    ledc_channel_t channel = LEDC_CHANNEL_1);
    ~Buzzer();

    Buzzer(const Buzzer&) = delete;
    Buzzer& operator=(const Buzzer&) = delete;

    /**
     * @brief Configura o timer e o canal LEDC com a saida em silencio.
     * @return ESP_OK em sucesso, ou codigo de erro do ESP-IDF.
     */
    esp_err_t init();

    /** @brief Toca o padrao do evento (tabela `pattern_for`). */
    void notify(SoundEvent event) { play(pattern_for(event)); }

    /**
     * @brief Comeca a tocar `pattern`, interrompendo o que estiver tocando se
     * a prioridade for igual ou maior (`priority`). Um padrao menos importante
     * e descartado, nao enfileirado. Ignorado se silenciado ou nao inicializado.
     */
    void play(SoundPattern pattern);

    /** @brief Interrompe a sequencia e silencia a saida. */
    void stop();

    /** @brief Avanca a sequencia; chamar a cada `kTickMs`. */
    void tick();

    /** @brief Modo silencioso: corta o que estiver tocando e ignora novos sons. */
    void set_muted(bool muted);

    /** @brief Tabela unica evento -> padrao (como `RgbLed::color_for`). */
    static SoundPattern pattern_for(SoundEvent event);

    /**
     * @brief Prioridade do padrao: aviso de rede (0) < confirmacoes (1) <
     * cafe pronto e vinculo (2) < erro (3).
     */
    static uint8_t priority(SoundPattern pattern);

    /** @brief Duracao total do padrao em ticks de `kTickMs` (0 para None). */
    static uint32_t duration_ticks(SoundPattern pattern);

    [[nodiscard]] bool is_initialized() const noexcept { return initialized_; }
    [[nodiscard]] bool muted() const noexcept { return muted_; }
    [[nodiscard]] bool playing() const noexcept { return pattern_ != SoundPattern::None; }
    [[nodiscard]] SoundPattern pattern() const noexcept { return pattern_; }
    /** @brief Frequencia que sai agora no pino (0 = silencio). */
    [[nodiscard]] uint16_t current_freq_hz() const noexcept { return current_freq_hz_; }

private:
    struct Melody {
        const Note* notes;
        uint8_t count;
    };

    static Melody melody_for(SoundPattern pattern);
    static uint32_t ticks_for(const Note& note);
    void start_note(uint8_t index);
    void tone(uint16_t freq_hz);
    void silence();

    gpio_num_t pin_;
    ledc_timer_t timer_;
    ledc_channel_t channel_;
    bool initialized_ = false;
    bool muted_ = false;

    SoundPattern pattern_ = SoundPattern::None;
    Melody melody_{nullptr, 0};
    uint8_t note_index_ = 0;
    uint32_t note_ticks_left_ = 0;
    uint16_t current_freq_hz_ = 0;
};

} // namespace cafey::drivers
