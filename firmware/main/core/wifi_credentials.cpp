#include "wifi_credentials.hpp"

#include <array>

namespace cafey::core {

namespace {
// Limites de wifi_config_t::sta (ssid[32], password[64]) do ESP-IDF, +1 para o
// terminador nulo.
constexpr size_t kSsidBufferLen = 33;
constexpr size_t kPasswordBufferLen = 65;
} // namespace

esp_err_t WifiCredentialsStore::save(const std::string& ssid, const std::string& password) {
    nvs_handle_t handle;
    esp_err_t err = nvs_open(kNamespace, NVS_READWRITE, &handle);
    if (err != ESP_OK) {
        return err;
    }

    err = nvs_set_str(handle, kKeySsid, ssid.c_str());
    if (err == ESP_OK) {
        err = nvs_set_str(handle, kKeyPass, password.c_str());
    }
    if (err == ESP_OK) {
        err = nvs_commit(handle);
    }
    nvs_close(handle);
    return err;
}

esp_err_t WifiCredentialsStore::load(std::string& ssid, std::string& password) const {
    nvs_handle_t handle;
    esp_err_t err = nvs_open(kNamespace, NVS_READONLY, &handle);
    if (err != ESP_OK) {
        return err;
    }

    std::array<char, kSsidBufferLen> ssid_buf{};
    std::array<char, kPasswordBufferLen> pass_buf{};
    size_t ssid_len = ssid_buf.size();
    size_t pass_len = pass_buf.size();

    err = nvs_get_str(handle, kKeySsid, ssid_buf.data(), &ssid_len);
    if (err == ESP_OK) {
        err = nvs_get_str(handle, kKeyPass, pass_buf.data(), &pass_len);
    }
    nvs_close(handle);

    if (err != ESP_OK) {
        return err;
    }

    ssid.assign(ssid_buf.data());
    password.assign(pass_buf.data());
    return ESP_OK;
}

bool WifiCredentialsStore::has_credentials() const {
    std::string ssid, password;
    return load(ssid, password) == ESP_OK;
}

esp_err_t WifiCredentialsStore::clear() {
    nvs_handle_t handle;
    esp_err_t err = nvs_open(kNamespace, NVS_READWRITE, &handle);
    if (err != ESP_OK) {
        return err;
    }

    nvs_erase_key(handle, kKeySsid);
    nvs_erase_key(handle, kKeyPass);
    err = nvs_commit(handle);
    nvs_close(handle);
    return err;
}

} // namespace cafey::core
