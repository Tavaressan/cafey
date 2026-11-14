#pragma once

#include <cstddef>
#include <cstdint>
#include <functional>
#include <string>

#include "reconnect_policy.hpp"
#include "wifi_round_tracker.hpp"
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
 * exponencial, historico de redes provisionadas em NVS e fallback entre redes
 * conhecidas.
 *
 * Uma rede nova provisionada so entra no historico persistido apos conexao bem-
 * sucedida (handle_got_ip), evitando que uma senha errada apague credenciais
 * validas. No boot e ao perder conexao, tenta as redes conhecidas em ordem MRU.
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
     * @brief Recebe novas credenciais (provisionamento) e tenta conectar com
     * elas, iniciando o temporizador de timeout de 20s. A rede so e persistida
     * no historico apos conexao bem-sucedida.
     */
    esp_err_t provision(const std::string& ssid, const std::string& password);

    /**
     * @brief Inicia a conexao usando as redes conhecidas gravadas em NVS.
     * @return ESP_ERR_NOT_FOUND se nao ha credenciais provisionadas.
     */
    esp_err_t connect_with_stored_credentials();

    /** @brief Chamado pelo handler de evento ao receber WIFI_EVENT_STA_DISCONNECTED. */
    void handle_disconnected(uint8_t reason = 0);

    /** @brief Chamado pelo handler de evento ao receber IP_EVENT_STA_GOT_IP. */
    void handle_got_ip(const std::string& ip = "");

    /** @brief Disparado quando o timeout de 20s de provisionamento expira. */
    void handle_provision_timeout();

    /** @brief Executa a proxima tentativa de conexao agendada pelo backoff. */
    void try_reconnect();

    /**
     * @brief Callbacks de conectividade (FW-21 / UC-04): chamados na task do event loop
     * ao obter IP e ao desconectar. Devem so postar para um Active Object ou notificar BLE.
     */
    void set_on_connected(std::function<void()> cb) { on_connected_ = std::move(cb); }
    void set_on_connected_ip(std::function<void(const std::string& ip)> cb) {
        on_connected_ip_ = std::move(cb);
    }
    void set_on_disconnected(std::function<void()> cb) { on_disconnected_ = std::move(cb); }
    void set_on_disconnected_reason(std::function<void(bool is_auth_failure)> cb) {
        on_disconnected_reason_ = std::move(cb);
    }
    void set_on_provision_timeout(std::function<void()> cb) {
        on_provision_timeout_ = std::move(cb);
    }

    [[nodiscard]] WifiState state() const noexcept { return state_; }
    [[nodiscard]] const std::string& ip() const noexcept { return ip_; }
    [[nodiscard]] const std::string& current_ssid() const noexcept { return connect_ssid_; }
    [[nodiscard]] uint32_t reconnect_attempts() const noexcept { return policy_.attempts(); }

    /** @brief Lista SSIDs salvos em ordem MRU, sem expor senhas. */
    [[nodiscard]] std::vector<WifiNetworkInfo> list_networks() const {
        return credentials_.list();
    }

    /**
     * @brief Esquece uma rede salva. Se for a rede atual/conectada, desconecta e
     * tenta a proxima rede salva; se nao houver outra, fica em NOT_PROVISIONED
     * aguardando provisionamento por BLE.
     */
    esp_err_t forget_network(const std::string& ssid);

    /**
     * @brief Esquece todas as redes salvas, desconecta e permanece apenas em BLE
     * (NOT_PROVISIONED), pronto para novo provisionamento.
     */
    esp_err_t forget_all_networks();

private:
    esp_err_t apply_sta_config(const std::string& ssid, const std::string& password);
    esp_err_t apply_and_connect(const std::string& ssid, const std::string& password);
    esp_err_t try_connect_known(size_t start_index);
    void schedule_reconnect(bool round_done);
    void stop_provision_timer();
    static bool is_auth_reason(uint8_t reason);

    ReconnectPolicy policy_;
    WifiRoundTracker round_;
    WifiCredentialsStore credentials_;
    WifiState state_ = WifiState::NOT_PROVISIONED;
    std::string ip_;
    bool initialized_ = false;
    bool has_pending_ = false;
    std::string pending_ssid_;
    std::string pending_password_;
    std::string connect_ssid_;
    std::string connect_password_;
    size_t connect_index_ = 0;
    std::function<void()> on_connected_;
    std::function<void(const std::string& ip)> on_connected_ip_;
    std::function<void()> on_disconnected_;
    std::function<void(bool is_auth_failure)> on_disconnected_reason_;
    std::function<void()> on_provision_timeout_;
#if defined(ESP_PLATFORM)
    esp_timer_handle_t reconnect_timer_ = nullptr;
    esp_timer_handle_t provision_timer_ = nullptr;
#endif
};

} // namespace cafey::core
