#pragma once

#include <cstdint>
#include <functional>

#include "core/active_object.hpp"
#include "drivers/dht11.hpp"

namespace cafey::app {

/**
 * @brief Active Object do ambiente: le o DHT11 (temperatura e umidade) a cada
 * 5 s e avisa quem se interessa quando o valor muda ou a cada minuto.
 *
 * A leitura e uma funcao injetada (`Reader`) para que os testes de host nao
 * precisem de sensor; no alvo, `attach_sensor()` liga o driver real.
 *
 * Regras:
 *  - leitura valida -> guarda; reporta se for a primeira, se mudou, ou a cada
 *    `kReportEveryTicks` amostras (heartbeat, para o app saber que o dado e fresco);
 *  - `kFailuresToFault` falhas seguidas -> reporta "indisponivel" uma vez e para
 *    de afirmar o ultimo valor (um sensor solto nao pode mostrar 24 C para sempre);
 *  - uma leitura valida depois da falha volta a reportar.
 */
class Ambiente : public cafey::core::ActiveObject {
public:
    using Reader = std::function<cafey::drivers::Dht11Status(cafey::drivers::Dht11Reading&)>;

    struct Sample {
        bool valid = false;
        int16_t temperature_dc = 0; // decimos de grau Celsius
        uint8_t humidity_pct = 0;
    };

    static constexpr uint32_t kSamplePeriodMs = 5000; // DHT11 pede >= 1 s entre leituras
    static constexpr uint32_t kReportEveryTicks = 12; // 12 x 5 s = 1 min
    static constexpr uint8_t kFailuresToFault = 3;

    Ambiente();

    /** Liga o driver real. O ponteiro precisa viver tanto quanto o AO. */
    void attach_sensor(cafey::drivers::Dht11* sensor);

    /** Substitui a leitura (testes). */
    void set_reader(Reader reader) { reader_ = std::move(reader); }

    /** Chamado a cada reporte, na task do AO. `valid=false` = sensor indisponivel. */
    void set_on_report(std::function<void(const Sample&)> cb) { on_report_ = std::move(cb); }

    [[nodiscard]] const Sample& last() const noexcept { return last_; }
    [[nodiscard]] uint8_t consecutive_failures() const noexcept { return failures_; }

protected:
    void dispatch(const cafey::core::Message&) override {}
    void on_tick() override;

private:
    void report(const Sample& s);

    Reader reader_;
    std::function<void(const Sample&)> on_report_;
    Sample last_;
    uint8_t failures_ = 0;
    uint32_t ticks_since_report_ = 0;
    bool fault_reported_ = false;
};

} // namespace cafey::app
