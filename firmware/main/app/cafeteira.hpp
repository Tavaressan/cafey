#pragma once

#include <cstdint>
#include <functional>
#include <string>

#include "core/active_object.hpp"
#include "core/clock.hpp"
#include "drivers/buzzer.hpp"
#include "drivers/relay.hpp"
#include "drivers/rgb_led.hpp"
#include "storage/event.hpp"
#include "storage/event_id_generator.hpp"
#include "storage/event_queue_store.hpp"
#include "storage/sound_settings_store.hpp"

namespace cafey::app {

/**
 * @brief Active Object da Cafeteira: maquina de estados do preparo,
 * acionamento do rele, LED RGB com cores de estado (spec §5.3), buzzer
 * passivo com padroes sonoros (#311), leitura do botao fisico e
 * enfileiramento de eventos (FW-22).
 */
class Cafeteira : public cafey::core::ActiveObject {
public:
    enum class State : uint8_t { Off, On };

    /**
     * @brief Desfecho do ultimo preparo (spec-backend §7.2, coluna `resultado`).
     * `Concluido` = corte pelo temporizador local; `Cancelado` = corte por
     * botao ou comando (UC-09).
     */
    enum class Result : uint8_t { None, Concluido, Cancelado };

    // Default de `duracaoS` quando o comando nao informa (spec-backend P9).
    static constexpr uint32_t kDefaultDurationS = 300;

    // Teto de seguranca para `duracaoS`, independente do backend: cobre o
    // fallback BLE (spec §6.4), que pode comandar a base sem passar pelo
    // `@Max(900)` de `ComandoRequest` (BE-27, PR #142). Ver issue #143.
    static constexpr uint32_t kMaxDurationS = 900;

    // Tempo em que o LED permanece em Branco Fixo apos cafe pronto (spec §5.3: alguns minutos).
    static constexpr uint32_t kReadyDurationS = 180;

    // Vinculo por janela do botao fisico (FW-24, #275): toque longo abre uma
    // janela curta em que a placa publica um codigo de uso unico.
    static constexpr uint32_t kLongPressMs = 5000;
    static constexpr uint32_t kBindWindowS = 120;

    // Intervalo minimo entre sons de rede (#311): Wi-Fi ou MQTT instavel nao
    // vira uma sequencia de bipes.
    static constexpr uint32_t kNetworkSoundCooldownS = 60;

    explicit Cafeteira(gpio_num_t relay_pin = GPIO_NUM_26,
                       gpio_num_t button_pin = GPIO_NUM_27,
                       gpio_num_t led_r = GPIO_NUM_21,
                       gpio_num_t led_g = GPIO_NUM_22,
                       gpio_num_t led_b = GPIO_NUM_23,
                       gpio_num_t buzzer_pin = GPIO_NUM_25);

    [[nodiscard]] State state() const noexcept { return state_; }
    [[nodiscard]] bool relay_on() const noexcept { return relay_.is_on(); }
    [[nodiscard]] Result last_result() const noexcept { return last_result_; }
    [[nodiscard]] uint32_t brew_seconds_left() const noexcept {
        return (brew_ticks_left_ + kTicksPerSecond - 1) / kTicksPerSecond;
    }
    [[nodiscard]] drivers::CoffeeVisualState visual_state() const noexcept { return visual_state_; }
    [[nodiscard]] const drivers::RgbLed& led() const noexcept { return led_; }
    [[nodiscard]] const drivers::Buzzer& buzzer() const noexcept { return buzzer_; }

    /** @brief Janela de vinculo aberta pelo toque longo (FW-24, #275). */
    [[nodiscard]] bool bind_window_open() const noexcept { return bind_ticks_left_ > 0; }
    /** @brief Codigo de uso unico da janela atual (vazio se fechada). */
    [[nodiscard]] const std::string& bind_code() const noexcept { return bind_code_; }
    /** @brief Segundos restantes da janela de vinculo (0 se fechada). */
    [[nodiscard]] uint32_t bind_seconds_left() const noexcept {
        return (bind_ticks_left_ + kTicksPerSecond - 1) / kTicksPerSecond;
    }

    /**
     * @brief Anexa a fila persistida e o gerador de ID para enfileirar eventos ao
     * finalizar ou cancelar um preparo (FW-22).
     */
    void attach_event_queue(cafey::storage::EventQueueStore* store,
                            cafey::storage::EventIdGenerator* id_gen,
                            cafey::core::Clock* clock = nullptr) {
        event_queue_ = store;
        event_id_gen_ = id_gen;
        if (clock != nullptr) clock_ = clock;
    }

    /**
     * @brief Anexa a preferencia de som persistida (#311). Lida em on_start()
     * e regravada a cada `SetMuted`. Definir antes de start().
     */
    void attach_sound_settings(cafey::storage::SoundSettingsStore* settings) {
        sound_settings_ = settings;
    }

    /**
     * @brief Notificacao disparada apos enfileirar um novo evento (para a
     * Conectividade drenar imediatamente via drain(), FW-22).
     */
    void set_on_event_queued(std::function<void()> on_event_queued) {
        on_event_queued_ = std::move(on_event_queued);
    }

    /**
     * @brief Informa diretamente se o NTP ja sincronizou (usado para sinalizar
     * horario_provisorio nos eventos enfileirados).
     */
    void set_ntp_synced(bool synced) { ntp_synced_ = synced; }

    /**
     * @brief Atualiza a condicao de rede para a cor/padrao do LED (spec §5.3).
     */
    void set_network_ok(bool ok);

    /**
     * @brief Notifica se ha agendamento armado ativo no Agendador (spec §5.3).
     */
    void set_schedules_armed(bool armed);

    /**
     * @brief Sinaliza estado de erro/falha no hardware (LED vermelho fixo).
     */
    void set_error(bool error);

    /**
     * @brief Callback chamado a cada aplicacao de estado (rele + LED), inclusive
     * no boot, com true = preparando. Usado para publicar `estado` (FW-21).
     * Definir antes de start().
     */
    void set_on_state_change(std::function<void(bool)> on_change) {
        on_state_change_ = std::move(on_change);
    }

    /**
     * @brief Callback da janela de vinculo (FW-24, #275): `aberta=true` traz o
     * codigo de uso unico para publicar em `dispositivos/<thing>/vinculo` e
     * expor por BLE; `aberta=false` limpa a caracteristica.
     */
    void set_on_bind_window_change(std::function<void(bool, const std::string&)> on_change) {
        on_bind_window_change_ = std::move(on_change);
    }

protected:
    void on_start() override;
    void on_tick() override; // polling do botao com debounce e animacao do LED
    void dispatch(const cafey::core::Message& msg) override;

private:
    static constexpr int kDebounceSamples = 3; // 3 x 10 ms = 30 ms estaveis
    // O tick do AO roda a cada 10 ms -> 100 ticks por segundo.
    static constexpr uint32_t kTicksPerSecond = 100;
    static constexpr uint32_t kLongPressTicks = kLongPressMs / 10;

    void apply_state();
    void set_muted(bool muted);
    void open_bind_window();
    void close_bind_window();
    static std::string generate_bind_code();
    void start_brew(uint32_t duration_s,
                    cafey::storage::EventOrigin origin = cafey::storage::EventOrigin::APP);
    void cancel_brew();
    void finish_brew();
    void enqueue_brew_event(cafey::storage::EventResult result, uint32_t duration_s);

    cafey::drivers::Relay relay_;
    cafey::drivers::RgbLed led_;
    cafey::drivers::Buzzer buzzer_;
    gpio_num_t button_pin_;
    cafey::storage::SoundSettingsStore* sound_settings_ = nullptr;
    // Ticks desde o ultimo som de rede; comeca saturado para o primeiro tocar.
    uint32_t ticks_since_network_sound_ = kNetworkSoundCooldownS * kTicksPerSecond;

    State state_ = State::Off;
    drivers::CoffeeVisualState visual_state_ = drivers::CoffeeVisualState::Idle;
    bool network_ok_ = true; // Por default assume conectado em testes locais
    bool has_armed_schedules_ = false;
    bool has_error_ = false;
    uint32_t ready_ticks_left_ = 0;

    uint32_t brew_ticks_total_ = 0;
    uint32_t brew_ticks_left_ = 0; // ticks restantes do preparo em curso
    uint32_t brew_start_epoch_ = 0;
    Result last_result_ = Result::None;
    cafey::storage::EventOrigin current_origin_ = cafey::storage::EventOrigin::APP;
    bool ntp_synced_ = false;

    cafey::storage::EventQueueStore* event_queue_ = nullptr;
    cafey::storage::EventIdGenerator* event_id_gen_ = nullptr;
    cafey::core::SystemClock default_clock_;
    cafey::core::Clock* clock_ = &default_clock_;
    std::function<void()> on_event_queued_;

    int btn_reading_ = 1;
    int btn_stable_ = 1;
    int btn_same_count_ = 0;
    uint32_t btn_press_ticks_ = 0;
    bool long_press_consumed_ = false;
    std::function<void(bool)> on_state_change_;

    uint32_t bind_ticks_left_ = 0;
    std::string bind_code_;
    std::function<void(bool, const std::string&)> on_bind_window_change_;
};

} // namespace cafey::app
