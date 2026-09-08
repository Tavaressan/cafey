#include "event_id_generator.hpp"

#include <cinttypes>
#include <cstdio>

namespace cafey::storage {

EventIdGenerator::EventIdGenerator(const char* nvs_namespace)
    : store_(nvs_namespace) {}

esp_err_t EventIdGenerator::init(uint32_t boot_id) {
    esp_err_t err = store_.init();
    if (err != ESP_OK) {
        return err;
    }

    uint32_t stored = 0;
    err = store_.load_blob(kSeqKey, &stored, sizeof(stored));
    if (err == ESP_ERR_NVS_NOT_FOUND) {
        stored = 0; // primeira execucao: comeca em 0
    } else if (err != ESP_OK) {
        return err;
    }

    boot_id_ = boot_id;
    seq_ = stored;
    initialized_ = true;
    return ESP_OK;
}

esp_err_t EventIdGenerator::next(char* out, size_t out_size) {
    if (!initialized_) {
        return ESP_ERR_INVALID_STATE;
    }
    if (out == nullptr || out_size < kMaxIdLen) {
        return ESP_ERR_INVALID_ARG;
    }

    std::snprintf(out, out_size, "%08" PRIx32 ":%" PRIu32, boot_id_, seq_);

    const uint32_t advanced = seq_ + 1;
    esp_err_t err = store_.save_blob(kSeqKey, &advanced, sizeof(advanced));
    if (err != ESP_OK) {
        return err;
    }
    seq_ = advanced;
    return ESP_OK;
}

} // namespace cafey::storage
