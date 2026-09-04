#pragma once

#include <cstdint>
#include <string>

#include "reconnect_policy.hpp"
#include "wifi_credentials.hpp"

#if defined(ESP_PLATFORM)
#include "esp_err.h"
#include "esp_event.h"
#include "esp_timer.h"
#include "esp_wifi.h"
#endif

namespace cafey::core {

/**
 * @brief Estado de conectividade Wi-Fi da cafeteira (UC-04).
 */
enum class WifiState {
    NOT_PROVISIONED,  // Sem credenciais gravadas: aguarda provisionamento (app/BLE)
    CONNECTING,        // esp_wifi_connect() emitido, aguardando IP_EVENT_STA_GOT_IP
    CONNECTED,         // Conectado e com IP valido
    RECONNECTING,      // Desconectado apos ter sido conectado; backoff em andamento
};

/**
 * @brief Gerencia a conexao Wi-Fi (modo STA) com reconexao automatica em backoff
 * exponencial e provisionamento de credenciais persistido em NVS.
 *
 * Integra-se ao ESP-IDF via esp_wifi/esp_event; a logica de backoff
 * (ReconnectPolicy) e a persistencia de credenciais (WifiCredentialsStore) sao
 * delegadas a componentes puros e testados isoladamente em host.
 *
 * So compilado para o alvo ESP32 real (ESP_PLATFORM); a logica pura das
 * dependencias e coberta por testes de host em firmware/test/.
 */
class WifiManager {
public:
    WifiManager() = default;

    /**
     * @brief Inicializa NVS, netif, o event loop padrao e o driver esp_wifi em
     * modo STA. Registra os handlers de desconexao/obtencao de IP.
     * @return ESP_OK em sucesso, ou codigo de erro do ESP-IDF.
     */
    esp_err_t init();

    /**
     * @brief Grava novas credenciais (provisionamento) e tenta conectar
     * imediatamente com elas.
     */
    esp_err_t provision(const std::string& ssid, const std::string& password);

    /**
     * @brief Inicia a conexao usando as credenciais ja gravadas em NVS.
     * @return ESP_ERR_NOT_FOUND se nao ha credenciais provisionadas.
     */
    esp_err_t connect_with_stored_credentials();

    /** @brief Chamado pelo handler de evento ao receber WIFI_EVENT_STA_DISCONNECTED. */
    void handle_disconnected();

    /** @brief Chamado pelo handler de evento ao receber IP_EVENT_STA_GOT_IP. */
    void handle_got_ip();

    [[nodiscard]] WifiState state() const noexcept { return state_; }
    [[nodiscard]] uint32_t reconnect_attempts() const noexcept { return policy_.attempts(); }

private:
    esp_err_t apply_sta_config(const std::string& ssid, const std::string& password);
    void schedule_reconnect();

    ReconnectPolicy policy_;
    WifiCredentialsStore credentials_;
    WifiState state_ = WifiState::NOT_PROVISIONED;
    bool initialized_ = false;
#if defined(ESP_PLATFORM)
    esp_timer_handle_t reconnect_timer_ = nullptr;
#endif
};

} // namespace cafey::core
