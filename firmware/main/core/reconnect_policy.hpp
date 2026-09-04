#pragma once

#include <cstdint>

namespace cafey::core {

/**
 * @brief Calcula o backoff exponencial entre tentativas de reconexao de Wi-Fi.
 *
 * O atraso dobra a cada tentativa falha, comecando em kBaseDelayMs e limitado
 * a kMaxDelayMs. Deve ser resetado apos uma conexao bem-sucedida (reset()).
 */
class ReconnectPolicy {
public:
    static constexpr uint32_t kBaseDelayMs = 1000;
    static constexpr uint32_t kMaxDelayMs = 60000;

    ReconnectPolicy() = default;

    /**
     * @brief Retorna o atraso (ms) a aguardar antes da proxima tentativa e avanca
     * o estado interno (contador de tentativas e proximo atraso).
     */
    uint32_t next_delay_ms();

    /**
     * @brief Reseta contador de tentativas e atraso para os valores base.
     * Deve ser chamado apos uma conexao Wi-Fi bem-sucedida.
     */
    void reset();

    /** @brief Numero de tentativas de reconexao realizadas desde o ultimo reset(). */
    [[nodiscard]] uint32_t attempts() const noexcept;

private:
    uint32_t attempts_ = 0;
    uint32_t current_delay_ms_ = kBaseDelayMs;
};

} // namespace cafey::core
