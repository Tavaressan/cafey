#pragma once

#include <cstdint>
#include <cstring>
#include <string>
#include <unordered_map>

// Mock minimo do subconjunto de NVS (Non-Volatile Storage) do ESP-IDF usado por
// cafey::core::WifiCredentialsStore, permitindo testes unitarios em host sem a
// toolchain/flash real do ESP32.

#ifndef ESP_OK
#define ESP_OK                    0
#define ESP_FAIL                 -1
#define ESP_ERR_INVALID_ARG       0x102
#define ESP_ERR_INVALID_STATE     0x103
#define ESP_ERR_INVALID_SIZE      0x104
typedef int esp_err_t;
#endif

#define ESP_ERR_NVS_BASE          0x1100
#define ESP_ERR_NVS_NOT_FOUND     (ESP_ERR_NVS_BASE + 1)

typedef uint32_t nvs_handle_t;

typedef enum {
    NVS_READONLY = 0,
    NVS_READWRITE = 1,
} nvs_open_mode_t;

class MockNvs {
public:
    static void reset() {
        store.clear();
        open_ret_code = ESP_OK;
        commit_ret_code = ESP_OK;
    }

    static std::unordered_map<std::string, std::string>& store_for(const std::string& ns) {
        return store[ns];
    }

    static esp_err_t open_ret_code;
    static esp_err_t commit_ret_code;

private:
    static std::unordered_map<std::string, std::unordered_map<std::string, std::string>> store;
};

inline std::unordered_map<std::string, std::unordered_map<std::string, std::string>> MockNvs::store;
inline esp_err_t MockNvs::open_ret_code = ESP_OK;
inline esp_err_t MockNvs::commit_ret_code = ESP_OK;

inline esp_err_t nvs_flash_init() {
    return ESP_OK;
}

// Mapa auxiliar handle -> namespace, para as chamadas nvs_get_str/nvs_set_str localizarem o
// armazenamento correto sem precisar repassar o nome do namespace a cada chamada.
inline std::unordered_map<nvs_handle_t, std::string>& mock_nvs_handle_namespaces() {
    static std::unordered_map<nvs_handle_t, std::string> handles;
    return handles;
}

inline esp_err_t nvs_open(const char *name, nvs_open_mode_t /*open_mode*/, nvs_handle_t *out_handle) {
    if (MockNvs::open_ret_code != ESP_OK) return MockNvs::open_ret_code;
    // Codifica o namespace como um "handle" simulado usando o hash da string.
    *out_handle = static_cast<nvs_handle_t>(std::hash<std::string>{}(name));
    mock_nvs_handle_namespaces()[*out_handle] = name;
    return ESP_OK;
}

inline esp_err_t nvs_set_str(nvs_handle_t handle, const char *key, const char *value) {
    auto it = mock_nvs_handle_namespaces().find(handle);
    if (it == mock_nvs_handle_namespaces().end()) return ESP_ERR_INVALID_STATE;
    MockNvs::store_for(it->second)[key] = value;
    return ESP_OK;
}

inline esp_err_t nvs_get_str(nvs_handle_t handle, const char *key, char *out_value, size_t *length) {
    auto it = mock_nvs_handle_namespaces().find(handle);
    if (it == mock_nvs_handle_namespaces().end()) return ESP_ERR_INVALID_STATE;
    auto &ns_store = MockNvs::store_for(it->second);
    auto value_it = ns_store.find(key);
    if (value_it == ns_store.end()) return ESP_ERR_NVS_NOT_FOUND;

    size_t needed = value_it->second.size() + 1;
    if (out_value == nullptr) {
        *length = needed;
        return ESP_OK;
    }
    if (*length < needed) {
        return ESP_ERR_INVALID_SIZE;
    }
    std::memcpy(out_value, value_it->second.c_str(), needed);
    *length = needed;
    return ESP_OK;
}

inline esp_err_t nvs_erase_key(nvs_handle_t handle, const char *key) {
    auto it = mock_nvs_handle_namespaces().find(handle);
    if (it == mock_nvs_handle_namespaces().end()) return ESP_ERR_INVALID_STATE;
    MockNvs::store_for(it->second).erase(key);
    return ESP_OK;
}

inline esp_err_t nvs_commit(nvs_handle_t /*handle*/) {
    return MockNvs::commit_ret_code;
}

inline void nvs_close(nvs_handle_t /*handle*/) {
}
