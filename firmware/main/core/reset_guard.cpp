#include "core/reset_guard.hpp"

namespace cafey::core {

namespace {
constexpr const char* kNamespace = "cafey_reset";
constexpr const char* kKeyCount = "cnt";
} // namespace

ResetAssessment assess_reset_reason(esp_reset_reason_t reason) {
    switch (reason) {
        case ESP_RST_TASK_WDT:
        case ESP_RST_WDT:
        case ESP_RST_INT_WDT:
            return {ResetKind::Watchdog, true, "RESET_WATCHDOG"};
        case ESP_RST_PANIC:
            return {ResetKind::Panic, true, "RESET_PANIC"};
        case ESP_RST_BROWNOUT:
            return {ResetKind::Brownout, true, "RESET_BROWNOUT"};
        default:
            return {ResetKind::Normal, false, "RESET_NORMAL"};
    }
}

ResetGuard::ResetGuard() : nvs_(kNamespace) {}

esp_err_t ResetGuard::init() {
    esp_err_t err = nvs_.init();
    if (err != ESP_OK) return err;
    uint32_t stored = 0;
    err = nvs_.load_blob(kKeyCount, &stored, sizeof(stored));
    if (err == ESP_ERR_NVS_NOT_FOUND) {
        abnormal_count_ = 0;
        return ESP_OK;
    }
    if (err != ESP_OK) return err;
    abnormal_count_ = stored;
    safe_mode_ = abnormal_count_ >= kSafeModeThreshold;
    return ESP_OK;
}

esp_err_t ResetGuard::persist() {
    return nvs_.save_blob(kKeyCount, &abnormal_count_, sizeof(abnormal_count_));
}

esp_err_t ResetGuard::record_boot(esp_reset_reason_t reason) {
    assessment_ = assess_reset_reason(reason);
    if (assessment_.abnormal) {
        ++abnormal_count_;
        safe_mode_ = abnormal_count_ >= kSafeModeThreshold;
        return persist();
    }
    // Boot limpo: mantem o contador ate prova de estabilidade (mark_stable),
    // mas recalcula safe_mode conforme o contador atual.
    safe_mode_ = abnormal_count_ >= kSafeModeThreshold;
    return ESP_OK;
}

esp_err_t ResetGuard::mark_stable() {
    if (abnormal_count_ == 0 && !safe_mode_) return ESP_OK;
    abnormal_count_ = 0;
    safe_mode_ = false;
    return persist();
}

} // namespace cafey::core
