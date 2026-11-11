#pragma once

#include <cstdint>

#include "core/task_wdt.hpp"
#include "storage/nvs_store.hpp"

namespace cafey::core {

enum class ResetKind : uint8_t {
    Normal = 0,
    Watchdog = 1,
    Panic = 2,
    Brownout = 3,
};

struct ResetAssessment {
    ResetKind kind = ResetKind::Normal;
    bool abnormal = false;
    // Codigo textual curto para evento/log (ex.: "RESET_WATCHDOG").
    const char* code = "RESET_NORMAL";
};

/**
 * @brief Mapeia o motivo de reset do ESP-IDF para uma classificacao estavel
 * usada em logs, eventos e modo seguro (FW-31).
 */
ResetAssessment assess_reset_reason(esp_reset_reason_t reason);

/**
 * @brief Rastreia resets anormais consecutivos em NVS e decide modo seguro.
 *
 * Persistencia (namespace `cafey_reset`):
 *  - chave `cnt`: uint32_t com o numero de resets anormais consecutivos.
 *
 * Regras:
 *  - boot com reset anormal: incrementa contador.
 *  - boot normal: nao altera sozinho; o contador e zerado ao atingir periodo
 *    estavel (mark_stable / 5 min sem reset) ou pode permanecer se ainda nao
 *    estabilizou (politica conservadora). Aqui: boot normal nao incrementa e
 *    o contador so zera em mark_stable(), evitando limpar um contador alto
 *    antes de provar estabilidade.
 *  - safe mode ativo quando contador >= kSafeModeThreshold.
 */
class ResetGuard {
public:
    static constexpr uint32_t kSafeModeThreshold = 3;
    static constexpr uint32_t kStablePeriodMs = 5u * 60u * 1000u;

    ResetGuard();

    esp_err_t init();
    esp_err_t record_boot(esp_reset_reason_t reason);
    esp_err_t mark_stable();

    [[nodiscard]] const ResetAssessment& assessment() const noexcept { return assessment_; }
    [[nodiscard]] uint32_t abnormal_count() const noexcept { return abnormal_count_; }
    [[nodiscard]] bool safe_mode() const noexcept { return safe_mode_; }

private:
    esp_err_t persist();

    storage::NvsStore nvs_;
    ResetAssessment assessment_;
    uint32_t abnormal_count_ = 0;
    bool safe_mode_ = false;
};

} // namespace cafey::core
