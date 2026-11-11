#include "sound_settings_store.hpp"

#include <cstdint>

namespace cafey::storage {

SoundSettingsStore::SoundSettingsStore(const char* nvs_namespace)
    : store_(nvs_namespace) {}

esp_err_t SoundSettingsStore::init() {
    esp_err_t err = store_.init();
    if (err != ESP_OK) {
        return err;
    }

    uint8_t stored = 0;
    err = store_.load_blob(kMutedKey, &stored, sizeof(stored));
    if (err == ESP_ERR_NVS_NOT_FOUND) {
        stored = 0; // primeira execucao: som ligado
    } else if (err != ESP_OK) {
        return err;
    }

    muted_ = stored != 0;
    initialized_ = true;
    return ESP_OK;
}

esp_err_t SoundSettingsStore::set_muted(bool muted) {
    if (!initialized_) {
        return ESP_ERR_INVALID_STATE;
    }
    if (muted == muted_) {
        return ESP_OK; // evita escrita desnecessaria na flash
    }
    const uint8_t value = muted ? 1 : 0;
    esp_err_t err = store_.save_blob(kMutedKey, &value, sizeof(value));
    if (err != ESP_OK) {
        return err;
    }
    muted_ = muted;
    return ESP_OK;
}

} // namespace cafey::storage
