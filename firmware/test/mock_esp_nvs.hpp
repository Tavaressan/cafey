#pragma once

// Minimal host-side mock of the ESP-IDF NVS (Non-Volatile Storage) API,
// mirroring mock_esp_gpio.hpp, so storage classes can be unit tested without
// the ESP-IDF toolchain. Storage is an in-memory map keyed by
// "namespace/key", simulating flash persistence within a single process.

#include <cstdint>
#include <cstring>
#include <string>
#include <unordered_map>
#include <vector>

#ifndef ESP_OK
#define ESP_OK 0
#endif
#ifndef ESP_FAIL
#define ESP_FAIL -1
#endif
#ifndef ESP_ERR_INVALID_ARG
#define ESP_ERR_INVALID_ARG 0x102
#endif
#ifndef ESP_ERR_INVALID_STATE
#define ESP_ERR_INVALID_STATE 0x103
#endif
#ifndef ESP_ERR_INVALID_SIZE
#define ESP_ERR_INVALID_SIZE 0x104
#endif

#define ESP_ERR_NVS_BASE 0x1100
#define ESP_ERR_NVS_NOT_FOUND (ESP_ERR_NVS_BASE + 0x01)
#define ESP_ERR_NVS_NO_FREE_PAGES (ESP_ERR_NVS_BASE + 0x0d)
#define ESP_ERR_NVS_NEW_VERSION_FOUND (ESP_ERR_NVS_BASE + 0x11)

typedef int esp_err_t;
typedef uint32_t nvs_handle_t;

typedef enum {
    NVS_READONLY = 0,
    NVS_READWRITE = 1,
} nvs_open_mode_t;

class MockNvs {
public:
    static void reset() {
        storage.clear();
        namespaces.clear();
        flash_init_ret_code = ESP_OK;
        open_ret_code = ESP_OK;
        set_blob_ret_code = ESP_OK;
        get_blob_ret_code = ESP_OK;
        commit_ret_code = ESP_OK;
        commit_call_count = 0;
    }

    static std::string make_key(const char* ns, const char* key) {
        return std::string(ns) + "/" + std::string(key);
    }

    static std::unordered_map<std::string, std::vector<uint8_t>> storage;
    static std::unordered_map<nvs_handle_t, std::string> namespaces;
    static nvs_handle_t next_handle;

    static esp_err_t flash_init_ret_code;
    static esp_err_t open_ret_code;
    static esp_err_t set_blob_ret_code;
    static esp_err_t get_blob_ret_code;
    static esp_err_t commit_ret_code;
    static int commit_call_count;
};

inline std::unordered_map<std::string, std::vector<uint8_t>> MockNvs::storage;
inline std::unordered_map<nvs_handle_t, std::string> MockNvs::namespaces;
inline nvs_handle_t MockNvs::next_handle = 1;
inline esp_err_t MockNvs::flash_init_ret_code = ESP_OK;
inline esp_err_t MockNvs::open_ret_code = ESP_OK;
inline esp_err_t MockNvs::set_blob_ret_code = ESP_OK;
inline esp_err_t MockNvs::get_blob_ret_code = ESP_OK;
inline esp_err_t MockNvs::commit_ret_code = ESP_OK;
inline int MockNvs::commit_call_count = 0;

inline esp_err_t nvs_flash_init() {
    return MockNvs::flash_init_ret_code;
}

inline esp_err_t nvs_flash_erase() {
    MockNvs::storage.clear();
    return ESP_OK;
}

inline esp_err_t nvs_open(const char* name, nvs_open_mode_t /*mode*/, nvs_handle_t* out_handle) {
    if (MockNvs::open_ret_code != ESP_OK) return MockNvs::open_ret_code;
    if (!name || !out_handle) return ESP_ERR_INVALID_ARG;
    nvs_handle_t handle = MockNvs::next_handle++;
    MockNvs::namespaces[handle] = name;
    *out_handle = handle;
    return ESP_OK;
}

inline esp_err_t nvs_get_blob(nvs_handle_t handle, const char* key, void* out_value, size_t* length) {
    if (MockNvs::get_blob_ret_code != ESP_OK) return MockNvs::get_blob_ret_code;
    if (!length) return ESP_ERR_INVALID_ARG;

    auto ns_it = MockNvs::namespaces.find(handle);
    if (ns_it == MockNvs::namespaces.end()) return ESP_ERR_INVALID_STATE;

    auto it = MockNvs::storage.find(MockNvs::make_key(ns_it->second.c_str(), key));
    if (it == MockNvs::storage.end()) {
        return ESP_ERR_NVS_NOT_FOUND;
    }

    if (out_value == nullptr) {
        // Caller is querying the required size only.
        *length = it->second.size();
        return ESP_OK;
    }

    size_t to_copy = it->second.size();
    *length = to_copy;
    std::memcpy(out_value, it->second.data(), to_copy);
    return ESP_OK;
}

inline esp_err_t nvs_set_blob(nvs_handle_t handle, const char* key, const void* value, size_t length) {
    if (MockNvs::set_blob_ret_code != ESP_OK) return MockNvs::set_blob_ret_code;

    auto ns_it = MockNvs::namespaces.find(handle);
    if (ns_it == MockNvs::namespaces.end()) return ESP_ERR_INVALID_STATE;

    const auto* bytes = static_cast<const uint8_t*>(value);
    MockNvs::storage[MockNvs::make_key(ns_it->second.c_str(), key)] =
        std::vector<uint8_t>(bytes, bytes + length);
    return ESP_OK;
}

inline esp_err_t nvs_commit(nvs_handle_t /*handle*/) {
    MockNvs::commit_call_count++;
    return MockNvs::commit_ret_code;
}

inline void nvs_close(nvs_handle_t handle) {
    MockNvs::namespaces.erase(handle);
}
