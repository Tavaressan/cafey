#pragma once

#include <cstdint>
#include <functional>

#include "core/active_object.hpp"
#include "core/clock.hpp"
#include "drivers/relay.hpp" // tambem traz o header de GPIO (real ou mock)
#include "storage/event.hpp"
#include "storage/event_id_generator.hpp"
#include "storage/event_queue_store.hpp"

namespace cafey::app {

/**
 * @brief Active Object da Cafeteira: maquina de estados do preparo,
 * acionamento do rele, LED RGB, leitura do botao fisico e enfileiramento de eventos (FW-22).
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
    // A issue #8 (HW-06) vai medir o tempo real de extracao e os dois tetos
    // devem ser revistos juntos quando ela fechar.
    static constexpr uint32_t kMaxDurationS = 900;

    explicit Cafeteira(gpio_num_t relay_pin = GPIO_NUM_26,
                       gpio_num_t button_pin = GPIO_NUM_27,
                       gpio_num_t led_r = GPIO_NUM_21,
                       gpio_num_t led_g = GPIO_NUM_22,
                       gpio_num_t led_b = GPIO_NUM_23);

    [[nodiscard]] State state() const noexcept { return state_; }
    [[nodiscard]] bool relay_on() const noexcept { return relay_.is_on(); }
    [[nodiscard]] Result last_result() const noexcept { return last_result_; }
    [[nodiscard]] uint32_t brew_seconds_left() const noexcept {
        return (brew_ticks_left_ + kTicksPerSecond - 1) / kTicksPerSecond;
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
     * @brief Callback chamado a cada aplicacao de estado (rele + LED), inclusive
     * no boot, com true = preparando. Usado para publicar `estado` (FW-21).
     * Definir antes de start().
     */
    void set_on_state_change(std::function<void(bool)> on_change) {
        on_state_change_ = std::move(on_change);
    }

protected:
    void on_start() override;
    void on_tick() override; // polling do botao com debounce
    void dispatch(const cafey::core::Message& msg) override;

private:
    static constexpr int kDebounceSamples = 3; // 3 x 10 ms = 30 ms estaveis
    // O tick do AO roda a cada 10 ms -> 100 ticks por segundo.
    static constexpr uint32_t kTicksPerSecond = 100;

    void apply_state();
    void led_set(bool r, bool g, bool b);
    void start_brew(uint32_t duration_s,
                    cafey::storage::EventOrigin origin = cafey::storage::EventOrigin::APP);
    void cancel_brew();
    void finish_brew();
    void enqueue_brew_event(cafey::storage::EventResult result, uint32_t duration_s);

    cafey::drivers::Relay relay_;
    gpio_num_t button_pin_;
    gpio_num_t led_r_;
    gpio_num_t led_g_;
    gpio_num_t led_b_;
    State state_ = State::Off;
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
    std::function<void(bool)> on_state_change_;
};

} // namespace cafey::app
