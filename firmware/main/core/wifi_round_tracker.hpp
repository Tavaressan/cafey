#pragma once

#include <cstddef>

namespace cafey::core {

/**
 * @brief Decide quando uma queda de Wi-Fi deve ser sinalizada (LED, BLE, estado
 * de rede) e quanto esperar antes da proxima tentativa.
 *
 * O dispositivo guarda varias redes. Uma rede que falha nao e motivo para avisar
 * "sem Wi-Fi": antes disso as outras redes conhecidas sao tentadas, uma apos a
 * outra, com um intervalo curto. So quando todas falham em sequencia (uma
 * "rodada") a falha e sinalizada, uma vez por queda, e as proximas rodadas
 * passam a respeitar o backoff exponencial.
 *
 * Logica pura, sem ESP-IDF: coberta por testes de host.
 */
class WifiRoundTracker {
public:
    /** Intervalo entre duas redes da mesma rodada (so o tempo de trocar de AP). */
    static constexpr unsigned kInterNetworkDelayMs = 500;

    struct Outcome {
        bool signal = false;     // avisar agora que nao ha conexao
        bool round_done = false; // todas as redes falharam; aplicar backoff
    };

    /** Registra uma falha de conexao com `known_networks` redes salvas (>= 1 efetivo). */
    Outcome on_failure(size_t known_networks) {
        const size_t needed = known_networks == 0 ? 1 : known_networks;
        Outcome out;
        if (++failures_in_round_ >= needed) {
            failures_in_round_ = 0;
            out.round_done = true;
            if (!signaled_) {
                signaled_ = true;
                out.signal = true;
            }
        }
        return out;
    }

    /** Conectou: zera a rodada e libera um novo aviso na proxima queda. */
    void on_connected() {
        failures_in_round_ = 0;
        signaled_ = false;
    }

    [[nodiscard]] size_t failures_in_round() const noexcept { return failures_in_round_; }
    [[nodiscard]] bool signaled() const noexcept { return signaled_; }

private:
    size_t failures_in_round_ = 0;
    bool signaled_ = false;
};

} // namespace cafey::core
