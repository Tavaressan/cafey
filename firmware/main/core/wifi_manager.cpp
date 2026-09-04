#include "wifi_manager.hpp"

#include <cstring>

#include "esp_log.h"
#include "esp_netif.h"
#include "esp_timer.h"
#include "esp_wifi.h"
#include "nvs_flash.h"

namespace cafey::core {

namespace {
constexpr const char* TAG = "WifiManager";

// Trampolins de evento: o ESP-IDF exige ponteiros de funcao livres (nao
// ponteiros a membro), entao repassamos `this` via arg_ e despachamos ao
// metodo correspondente da instancia.
void on_wifi_event(void* arg, esp_event_base_t event_base, int32_t event_id, void* /*event_data*/) {
    auto* self = static_cast<WifiManager*>(arg);
    if (event_base == WIFI_EVENT && event_id == WIFI_EVENT_STA_DISCONNECTED) {
        self->handle_disconnected();
    }
}

void on_ip_event(void* arg, esp_event_base_t event_base, int32_t event_id, void* /*event_data*/) {
    auto* self = static_cast<WifiManager*>(arg);
    if (event_base == IP_EVENT && event_id == IP_EVENT_STA_GOT_IP) {
        self->handle_got_ip();
    }
}

// Callback do esp_timer (one-shot) que dispara a proxima tentativa de conexao
// apos o atraso de backoff, sem bloquear a task do event loop padrao.
void on_reconnect_timer(void* arg) {
    (void)arg;
    esp_wifi_connect();
}
} // namespace

esp_err_t WifiManager::init() {
    esp_err_t err = nvs_flash_init();
    if (err == ESP_ERR_NVS_NO_FREE_PAGES || err == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        // Particao NVS corrompida ou de versao antiga: apaga e tenta novamente.
        err = nvs_flash_erase();
        if (err != ESP_OK) return err;
        err = nvs_flash_init();
    }
    if (err != ESP_OK) return err;

    err = esp_netif_init();
    if (err != ESP_OK) return err;

    err = esp_event_loop_create_default();
    if (err != ESP_OK && err != ESP_ERR_INVALID_STATE) {
        // ESP_ERR_INVALID_STATE = event loop ja existente (ok em reinicializacao).
        return err;
    }

    esp_netif_create_default_wifi_sta();

    wifi_init_config_t cfg = WIFI_INIT_CONFIG_DEFAULT();
    err = esp_wifi_init(&cfg);
    if (err != ESP_OK) return err;

    err = esp_event_handler_instance_register(WIFI_EVENT, ESP_EVENT_ANY_ID, &on_wifi_event, this, nullptr);
    if (err != ESP_OK) return err;

    err = esp_event_handler_instance_register(IP_EVENT, IP_EVENT_STA_GOT_IP, &on_ip_event, this, nullptr);
    if (err != ESP_OK) return err;

    err = esp_wifi_set_mode(WIFI_MODE_STA);
    if (err != ESP_OK) return err;

    err = esp_wifi_start();
    if (err != ESP_OK) return err;

    initialized_ = true;

    if (credentials_.has_credentials()) {
        return connect_with_stored_credentials();
    }

    state_ = WifiState::NOT_PROVISIONED;
    ESP_LOGI(TAG, "Sem credenciais provisionadas; aguardando provisionamento (UC-04)");
    return ESP_OK;
}

esp_err_t WifiManager::apply_sta_config(const std::string& ssid, const std::string& password) {
    wifi_config_t wifi_config = {};
    std::strncpy(reinterpret_cast<char*>(wifi_config.sta.ssid), ssid.c_str(), sizeof(wifi_config.sta.ssid) - 1);
    std::strncpy(reinterpret_cast<char*>(wifi_config.sta.password), password.c_str(), sizeof(wifi_config.sta.password) - 1);
    wifi_config.sta.threshold.authmode = password.empty() ? WIFI_AUTH_OPEN : WIFI_AUTH_WPA2_PSK;

    return esp_wifi_set_config(WIFI_IF_STA, &wifi_config);
}

esp_err_t WifiManager::provision(const std::string& ssid, const std::string& password) {
    esp_err_t err = credentials_.save(ssid, password);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "Falha ao gravar credenciais em NVS: %d", err);
        return err;
    }

    ESP_LOGI(TAG, "Credenciais provisionadas para SSID '%s'", ssid.c_str());
    policy_.reset();
    return connect_with_stored_credentials();
}

esp_err_t WifiManager::connect_with_stored_credentials() {
    if (!initialized_) {
        return ESP_ERR_INVALID_STATE;
    }

    std::string ssid, password;
    esp_err_t err = credentials_.load(ssid, password);
    if (err != ESP_OK) {
        state_ = WifiState::NOT_PROVISIONED;
        return err;
    }

    err = apply_sta_config(ssid, password);
    if (err != ESP_OK) return err;

    state_ = WifiState::CONNECTING;
    ESP_LOGI(TAG, "Conectando ao SSID '%s'...", ssid.c_str());
    return esp_wifi_connect();
}

void WifiManager::handle_disconnected() {
    if (state_ == WifiState::NOT_PROVISIONED) {
        // Desconexao sem provisionamento ainda: nada a reconectar.
        return;
    }

    state_ = WifiState::RECONNECTING;
    schedule_reconnect();
}

void WifiManager::handle_got_ip() {
    state_ = WifiState::CONNECTED;
    policy_.reset();
    ESP_LOGI(TAG, "Wi-Fi conectado, IP obtido");
}

void WifiManager::schedule_reconnect() {
    uint32_t delay_ms = policy_.next_delay_ms();
    ESP_LOGW(TAG, "Wi-Fi desconectado; nova tentativa em %u ms (tentativa #%u)",
             static_cast<unsigned>(delay_ms), static_cast<unsigned>(policy_.attempts()));

    if (reconnect_timer_ == nullptr) {
        // Zero-init explicito (em vez de designated initializer parcial) para
        // continuar compilando sob -Werror=missing-field-initializers quando o
        // ESP-IDF adiciona campos a esp_timer_create_args_t entre versoes.
        esp_timer_create_args_t timer_args = {};
        timer_args.callback = &on_reconnect_timer;
        timer_args.arg = nullptr;
        timer_args.dispatch_method = ESP_TIMER_TASK;
        timer_args.name = "wifi_reconnect";
        if (esp_timer_create(&timer_args, &reconnect_timer_) != ESP_OK) {
            // Fallback defensivo: se a criacao do timer falhar (ex.: sem memoria),
            // tenta reconectar imediatamente em vez de nunca mais tentar.
            esp_wifi_connect();
            return;
        }
    }
    esp_timer_start_once(reconnect_timer_, static_cast<uint64_t>(delay_ms) * 1000ULL);
}

} // namespace cafey::core
