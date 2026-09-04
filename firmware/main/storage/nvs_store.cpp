#include "nvs_store.hpp"

namespace cafey::storage {

NvsStore::NvsStore(const char* nvs_namespace)
    : namespace_(nvs_namespace),
      handle_(0),
      is_initialized_(false) {}

NvsStore::~NvsStore() {
    if (is_initialized_) {
        nvs_close(handle_);
    }
}

esp_err_t NvsStore::init() {
    core::LockGuard lock(mutex_);

    if (is_initialized_) {
        return ESP_OK;
    }

    esp_err_t err = nvs_flash_init();
    if (err == ESP_ERR_NVS_NO_FREE_PAGES || err == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        // Partition truncated or format changed: erase and retry once.
        nvs_flash_erase();
        err = nvs_flash_init();
    }
    if (err != ESP_OK) {
        return err;
    }

    err = nvs_open(namespace_, NVS_READWRITE, &handle_);
    if (err != ESP_OK) {
        return err;
    }

    is_initialized_ = true;
    return ESP_OK;
}

esp_err_t NvsStore::load_blob(const char* key, void* out_value, size_t value_size) {
    core::LockGuard lock(mutex_);

    if (!is_initialized_) {
        return ESP_ERR_INVALID_STATE;
    }

    size_t actual_size = value_size;
    esp_err_t err = nvs_get_blob(handle_, key, out_value, &actual_size);
    if (err != ESP_OK) {
        return err;
    }
    if (actual_size != value_size) {
        return ESP_ERR_INVALID_SIZE;
    }
    return ESP_OK;
}

esp_err_t NvsStore::save_blob(const char* key, const void* value, size_t value_size) {
    core::LockGuard lock(mutex_);

    if (!is_initialized_) {
        return ESP_ERR_INVALID_STATE;
    }

    esp_err_t err = nvs_set_blob(handle_, key, value, value_size);
    if (err != ESP_OK) {
        return err;
    }
    return nvs_commit(handle_);
}

} // namespace cafey::storage
