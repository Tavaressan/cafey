#include "mqtt_config_store.hpp"

#include <string>
#include <vector>

namespace cafey::core {

namespace {

// Le uma string de tamanho arbitrario de NVS: primeira chamada dimensiona,
// segunda copia. Certificados PEM passam de 1 KB, entao nao cabe buffer fixo.
esp_err_t read_str(nvs_handle_t handle, const char* key, std::string& out) {
    size_t needed = 0;
    esp_err_t err = nvs_get_str(handle, key, nullptr, &needed);
    if (err != ESP_OK) {
        return err;
    }
    std::vector<char> buffer(needed);
    err = nvs_get_str(handle, key, buffer.data(), &needed);
    if (err != ESP_OK) {
        return err;
    }
    out.assign(buffer.data());
    return ESP_OK;
}

} // namespace

esp_err_t MqttConfigStore::save(const MqttConfig& config) {
    nvs_handle_t handle;
    esp_err_t err = nvs_open(kNamespace, NVS_READWRITE, &handle);
    if (err != ESP_OK) {
        return err;
    }

    err = nvs_set_str(handle, kKeyBroker, config.broker_uri.c_str());
    if (err == ESP_OK) err = nvs_set_str(handle, kKeyClientId, config.client_id.c_str());
    if (err == ESP_OK) err = nvs_set_str(handle, kKeyRootCa, config.root_ca_pem.c_str());
    if (err == ESP_OK) err = nvs_set_str(handle, kKeyCert, config.device_cert_pem.c_str());
    if (err == ESP_OK) err = nvs_set_str(handle, kKeyKey, config.private_key_pem.c_str());
    if (err == ESP_OK) err = nvs_commit(handle);

    nvs_close(handle);
    return err;
}

esp_err_t MqttConfigStore::load(MqttConfig& config) const {
    nvs_handle_t handle;
    esp_err_t err = nvs_open(kNamespace, NVS_READONLY, &handle);
    if (err != ESP_OK) {
        return err;
    }

    err = read_str(handle, kKeyBroker, config.broker_uri);
    if (err == ESP_OK) err = read_str(handle, kKeyClientId, config.client_id);
    if (err == ESP_OK) err = read_str(handle, kKeyRootCa, config.root_ca_pem);
    if (err == ESP_OK) err = read_str(handle, kKeyCert, config.device_cert_pem);
    if (err == ESP_OK) err = read_str(handle, kKeyKey, config.private_key_pem);

    nvs_close(handle);
    return err;
}

bool MqttConfigStore::has_config() const {
    MqttConfig config;
    return load(config) == ESP_OK;
}

} // namespace cafey::core
