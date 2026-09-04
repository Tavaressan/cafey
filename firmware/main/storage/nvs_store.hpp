#pragma once

#include <cstddef>
#include <cstdint>

#if defined(ESP_PLATFORM)
#include "esp_err.h"
#include "nvs.h"
#include "nvs_flash.h"
#else
#include "mock_esp_nvs.hpp"
#endif

#include "core/mutex_lock.hpp"

namespace cafey::storage {

/**
 * @brief Thin RAII wrapper around a single ESP-IDF NVS (Non-Volatile Storage)
 * namespace, protected by a mutex (spec §5.4: "Acesso ao NVS protegido por
 * mutex — único ponto de coordenação entre tarefas").
 *
 * Blobs are the only storage primitive exposed: callers persist POD structs
 * (schedules, event queue) as fixed-layout binary blobs under a single key.
 */
class NvsStore {
public:
    explicit NvsStore(const char* nvs_namespace);
    ~NvsStore();

    NvsStore(const NvsStore&) = delete;
    NvsStore& operator=(const NvsStore&) = delete;

    /**
     * @brief Opens the NVS namespace (idempotent). Must be called before
     * load_blob()/save_blob(). Calls nvs_flash_init() internally on first use.
     * @return ESP_OK on success, or ESP-IDF error code.
     */
    esp_err_t init();

    /**
     * @brief Loads a blob previously stored under `key`.
     * @param key NVS key (max 15 chars, per ESP-IDF NVS limits).
     * @param out_value Destination buffer.
     * @param value_size Expected/actual size in bytes (exact match required).
     * @return ESP_OK on success, ESP_ERR_NVS_NOT_FOUND if key is absent,
     * or ESP-IDF error code.
     */
    esp_err_t load_blob(const char* key, void* out_value, size_t value_size);

    /**
     * @brief Persists a blob under `key` and commits immediately, so the
     * value survives reboot and power loss (spec §5.4).
     * @return ESP_OK on success, or ESP-IDF error code.
     */
    esp_err_t save_blob(const char* key, const void* value, size_t value_size);

    [[nodiscard]] bool is_initialized() const noexcept { return is_initialized_; }

private:
    const char* namespace_;
    nvs_handle_t handle_;
    bool is_initialized_;
    core::Mutex mutex_;
};

} // namespace cafey::storage
