#pragma once

#include "mqtt_client.hpp"

#if defined(ESP_PLATFORM)
#include "esp_err.h"
#include "nvs.h"
#include "nvs_flash.h"
#else
#include "mock_esp_nvs.hpp"
#endif

namespace cafey::core {

/**
 * @brief Persiste a configuracao MQTT/TLS (broker, client id, CA raiz,
 * certificado X.509 e chave privada do dispositivo) em NVS (FW-10, spec §6.1).
 *
 * As credenciais sao provisionadas por fora (fabrica/app) e carregadas no boot;
 * nunca ficam embutidas no binario.
 */
class MqttConfigStore {
public:
    MqttConfigStore() = default;

    /** @brief Grava a configuracao, sobrescrevendo qualquer valor anterior. */
    esp_err_t save(const MqttConfig& config);

    /**
     * @brief Carrega a configuracao previamente gravada. Os campos de Last Will
     * nao sao persistidos: o chamador os define a partir de MqttTopics.
     * @return ESP_OK em sucesso, ESP_ERR_NVS_NOT_FOUND se nunca provisionado.
     */
    esp_err_t load(MqttConfig& config) const;

    /** @brief true se ha configuracao gravada. */
    [[nodiscard]] bool has_config() const;

private:
    static constexpr const char* kNamespace = "cafey_mqtt";
    static constexpr const char* kKeyBroker = "broker";
    static constexpr const char* kKeyClientId = "clientid";
    static constexpr const char* kKeyRootCa = "rootca";
    static constexpr const char* kKeyCert = "cert";
    static constexpr const char* kKeyKey = "key";
};

} // namespace cafey::core
