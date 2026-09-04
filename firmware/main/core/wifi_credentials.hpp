#pragma once

#include <cstdint>
#include <string>

#if defined(ESP_PLATFORM)
#include "esp_err.h"
#include "nvs.h"
#include "nvs_flash.h"
#else
#include "mock_esp_nvs.hpp"
#endif

namespace cafey::core {

/**
 * @brief Persiste as credenciais de provisionamento Wi-Fi (SSID/senha) em NVS.
 *
 * Usado pelo fluxo de provisionamento (UC-04): quando o app/BLE informa novas
 * credenciais, elas sao gravadas aqui; no boot, o WifiManager consulta
 * has_credentials()/load() para decidir entre conectar direto ou aguardar
 * provisionamento.
 */
class WifiCredentialsStore {
public:
    WifiCredentialsStore() = default;

    /**
     * @brief Grava SSID e senha em NVS, sobrescrevendo qualquer valor anterior.
     * @return ESP_OK em sucesso, ou codigo de erro do ESP-IDF.
     */
    esp_err_t save(const std::string& ssid, const std::string& password);

    /**
     * @brief Carrega SSID e senha previamente gravados.
     * @return ESP_OK em sucesso, ESP_ERR_NVS_NOT_FOUND se nunca provisionado.
     */
    esp_err_t load(std::string& ssid, std::string& password) const;

    /** @brief true se ha credenciais gravadas (provisionamento ja realizado). */
    [[nodiscard]] bool has_credentials() const;

    /** @brief Apaga as credenciais gravadas (ex.: reset de fabrica / reprovisionamento). */
    esp_err_t clear();

private:
    static constexpr const char* kNamespace = "cafey_wifi";
    static constexpr const char* kKeySsid = "ssid";
    static constexpr const char* kKeyPass = "password";
};

} // namespace cafey::core
