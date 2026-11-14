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

void on_wifi_event(void* arg, esp_event_base_t event_base, int32_t event_id, void* event_data) {
    auto* self = static_cast<WifiManager*>(arg);
    if (event_base == WIFI_EVENT && event_id == WIFI_EVENT_STA_DISCONNECTED) {
        uint8_t reason = 0;
        if (event_data != nullptr) {
            auto* event = static_cast<wifi_event_sta_disconnected_t*>(event_data);
            reason = event->reason;
        }
        self->handle_disconnected(reason);
    }
}

void on_ip_event(void* arg, esp_event_base_t event_base, int32_t event_id, void* event_data) {
    auto* self = static_cast<WifiManager*>(arg);
    if (event_base == IP_EVENT && event_id == IP_EVENT_STA_GOT_IP) {
        std::string ip_str;
        if (event_data != nullptr) {
            auto* event = static_cast<ip_event_got_ip_t*>(event_data);
            char buf[16] = {0};
            esp_ip4addr_ntoa(&event->ip_info.ip, buf, sizeof(buf));
            ip_str = buf;
        }
        self->handle_got_ip(ip_str);
    }
}

void on_reconnect_timer(void* arg) {
    auto* self = static_cast<WifiManager*>(arg);
    if (self != nullptr) {
        self->try_reconnect();
    }
}

void on_provision_timeout_timer(void* arg) {
    auto* self = static_cast<WifiManager*>(arg);
    if (self != nullptr) {
        self->handle_provision_timeout();
    }
}
} // namespace

bool WifiManager::is_auth_reason(uint8_t reason) {
    // WIFI_REASON_AUTH_EXPIRE(2), AUTH_FAIL/4WAY_HANDSHAKE_TIMEOUT variants(15),
    // e codigos de autenticacao reportados por alguns drivers (202, 204).
    return reason == 2 || reason == 15 || reason == 202 || reason == 204;
}

esp_err_t WifiManager::init() {
    esp_err_t err = nvs_flash_init();
    if (err == ESP_ERR_NVS_NO_FREE_PAGES || err == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        const char* reason = (err == ESP_ERR_NVS_NO_FREE_PAGES)
                                 ? "sem paginas livres"
                                 : "versao do NVS incompativel";
        // O apagamento da particao NVS remove agendamentos, fila de eventos e
        // credenciais MQTT/Wi-Fi. E inevitavel aqui porque nvs_flash_init() ja
        // falhou; registramos o motivo antes de prosseguir.
        ESP_LOGE(TAG,
                 "NVS indisponivel (%s, err=%d): apagando a particao inteira — "
                 "agendamentos, fila de eventos e credenciais serao perdidos",
                 reason, static_cast<int>(err));
        err = nvs_flash_erase();
        if (err != ESP_OK) return err;
        err = nvs_flash_init();
    }
    if (err != ESP_OK) return err;

    err = esp_netif_init();
    if (err != ESP_OK) return err;

    err = esp_event_loop_create_default();
    if (err != ESP_OK && err != ESP_ERR_INVALID_STATE) {
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
    std::strncpy(reinterpret_cast<char*>(wifi_config.sta.ssid), ssid.c_str(),
                 sizeof(wifi_config.sta.ssid) - 1);
    std::strncpy(reinterpret_cast<char*>(wifi_config.sta.password), password.c_str(),
                 sizeof(wifi_config.sta.password) - 1);
    wifi_config.sta.threshold.authmode = password.empty() ? WIFI_AUTH_OPEN : WIFI_AUTH_WPA2_PSK;

    return esp_wifi_set_config(WIFI_IF_STA, &wifi_config);
}

esp_err_t WifiManager::apply_and_connect(const std::string& ssid, const std::string& password) {
    esp_err_t err = apply_sta_config(ssid, password);
    if (err != ESP_OK) return err;

    connect_ssid_ = ssid;
    connect_password_ = password;
    state_ = WifiState::CONNECTING;
    ESP_LOGI(TAG, "Conectando ao SSID '%s'...", ssid.c_str());
    return esp_wifi_connect();
}

esp_err_t WifiManager::try_connect_known(size_t start_index) {
    if (!initialized_) {
        return ESP_ERR_INVALID_STATE;
    }

    const size_t n = credentials_.count();
    if (n == 0) {
        state_ = WifiState::NOT_PROVISIONED;
        return ESP_ERR_NOT_FOUND;
    }

    // Tenta redes conhecidas em ordem MRU a partir de start_index (ciclico).
    // Filtro por scan e desejavel ("se possivel"), mas o fallback ordenado ja
    // garante que todas as redes conhecidas sejam tentadas.
    for (size_t i = 0; i < n; ++i) {
        const size_t idx = (start_index + i) % n;
        std::string ssid, password;
        if (credentials_.get(idx, ssid, password) == ESP_OK) {
            connect_index_ = idx;
            return apply_and_connect(ssid, password);
        }
    }

    state_ = WifiState::NOT_PROVISIONED;
    return ESP_ERR_NOT_FOUND;
}

esp_err_t WifiManager::provision(const std::string& ssid, const std::string& password) {
    // A rede provisionada fica apenas em memoria ate uma conexao bem-sucedida
    // (handle_got_ip), para que uma senha errada nao apague nem substitua
    // credenciais validas ja gravadas.
    has_pending_ = true;
    pending_ssid_ = ssid;
    pending_password_ = password;
    policy_.reset();

    ESP_LOGI(TAG, "Provisionamento recebido para SSID '%s'", ssid.c_str());

    if (provision_timer_ == nullptr) {
        esp_timer_create_args_t timer_args = {};
        timer_args.callback = &on_provision_timeout_timer;
        timer_args.arg = this;
        timer_args.dispatch_method = ESP_TIMER_TASK;
        timer_args.name = "wifi_prov_timeout";
        esp_timer_create(&timer_args, &provision_timer_);
    }
    if (provision_timer_ != nullptr) {
        esp_timer_stop(provision_timer_);
        esp_timer_start_once(provision_timer_, 20ULL * 1000ULL * 1000ULL);
    }

    if (state_ == WifiState::CONNECTED) {
        esp_err_t err = apply_sta_config(ssid, password);
        if (err != ESP_OK) return err;
        connect_ssid_ = ssid;
        connect_password_ = password;
        ESP_LOGI(TAG, "Ja conectado; reconectando para validar as credenciais novas");
        return esp_wifi_disconnect();
    }

    return apply_and_connect(ssid, password);
}

esp_err_t WifiManager::connect_with_stored_credentials() {
    if (!initialized_) {
        return ESP_ERR_INVALID_STATE;
    }
    connect_index_ = 0;
    return try_connect_known(0);
}

void WifiManager::handle_disconnected(uint8_t reason) {
    if (state_ == WifiState::NOT_PROVISIONED && !has_pending_) {
        return;
    }

    state_ = WifiState::RECONNECTING;
    const bool is_auth = is_auth_reason(reason);

    // Provisionamento em curso: o usuario espera o resultado do candidato, entao a
    // falha e sinalizada na hora. Sem candidato, uma rede que falha nao e motivo
    // para avisar: as outras redes conhecidas sao tentadas primeiro, e so a rodada
    // inteira sem sucesso e sinalizada (uma vez por queda).
    bool signal = true;
    bool round_done = true;
    if (!has_pending_) {
        const auto outcome = round_.on_failure(credentials_.count());
        signal = outcome.signal;
        round_done = outcome.round_done;
        if (!round_done) {
            ESP_LOGW(TAG, "Falha ao conectar em '%s' (motivo %u); tentando a proxima rede salva",
                     connect_ssid_.c_str(), static_cast<unsigned>(reason));
        } else if (signal) {
            ESP_LOGW(TAG, "Nenhuma das %u rede(s) salva(s) conectou",
                     static_cast<unsigned>(credentials_.count()));
        }
    }
    if (signal) {
        if (on_disconnected_reason_) on_disconnected_reason_(is_auth);
        if (on_disconnected_) on_disconnected_();
    }

    if (is_auth && has_pending_) {
        // Senha errada no provisionamento pendente: descarta o candidato (nunca
        // foi persistido) e volta a tentar as redes conhecidas, preservando a lista.
        ESP_LOGW(TAG, "Falha de autenticacao no provisionamento do SSID '%s'; "
                      "candidato descartado sem alterar redes salvas",
                 pending_ssid_.c_str());
        has_pending_ = false;
        pending_ssid_.clear();
        pending_password_.clear();
        stop_provision_timer();
        // try_reconnect() avanca (index+1): max faz o proximo ser 0 (a mais recente).
        connect_index_ = static_cast<size_t>(-1);
        policy_.reset();
    }

    schedule_reconnect(round_done);
}

void WifiManager::handle_got_ip(const std::string& ip) {
    state_ = WifiState::CONNECTED;
    ip_ = ip;
    policy_.reset();
    round_.on_connected();
    connect_index_ = 0;
    stop_provision_timer();

    // Persiste/promove a rede apenas apos sucesso: evita gravar senha errada e
    // garante ordem MRU (ultima conectada com sucesso no topo).
    if (!connect_ssid_.empty()) {
        esp_err_t err = credentials_.record_success(connect_ssid_, connect_password_);
        if (err != ESP_OK) {
            ESP_LOGW(TAG, "Falha ao registrar rede conectada no historico: %d",
                     static_cast<int>(err));
        }
    }
    if (has_pending_ && connect_ssid_ == pending_ssid_) {
        has_pending_ = false;
        pending_ssid_.clear();
        pending_password_.clear();
    }

    ESP_LOGI(TAG, "Wi-Fi conectado, IP obtido: %s", ip.c_str());
    if (on_connected_ip_) on_connected_ip_(ip_);
    if (on_connected_) on_connected_();
}

void WifiManager::handle_provision_timeout() {
    ESP_LOGW(TAG, "Timeout no provisionamento Wi-Fi (20s decorridos sem conexao)");
    // Nao limpa o candidato pendente nem altera a lista: o resultado (sucesso ou
    // falha de autenticacao) ainda pode chegar; em caso de falha, a lista de
    // redes salvas permanece intacta.
    if (on_provision_timeout_) on_provision_timeout_();
}

void WifiManager::stop_provision_timer() {
    if (provision_timer_ != nullptr) {
        esp_timer_stop(provision_timer_);
    }
}

esp_err_t WifiManager::forget_network(const std::string& ssid) {
    const bool is_current =
        (state_ == WifiState::CONNECTED || state_ == WifiState::CONNECTING ||
         state_ == WifiState::RECONNECTING) &&
        ssid == connect_ssid_;

    esp_err_t err = credentials_.forget(ssid);
    if (err != ESP_OK) return err;

    if (has_pending_ && pending_ssid_ == ssid) {
        has_pending_ = false;
        pending_ssid_.clear();
        pending_password_.clear();
        stop_provision_timer();
    }

    if (!is_current) {
        return ESP_OK;
    }

    if (credentials_.count() == 0) {
        // Sem redes salvas: desconecta e fica aguardando provisionamento por BLE.
        state_ = WifiState::NOT_PROVISIONED;
        connect_ssid_.clear();
        connect_password_.clear();
        ip_.clear();
        policy_.reset();
        if (reconnect_timer_ != nullptr) {
            esp_timer_stop(reconnect_timer_);
        }
        esp_wifi_disconnect();
        return ESP_OK;
    }

    // Tenta a proxima rede salva. Desconecta para forcar nova tentativa com a
    // lista atualizada; handle_disconnected agenda o reconnect.
    connect_index_ = 0;
    policy_.reset();
    esp_wifi_disconnect();
    return ESP_OK;
}

esp_err_t WifiManager::forget_all_networks() {
    has_pending_ = false;
    pending_ssid_.clear();
    pending_password_.clear();
    connect_ssid_.clear();
    connect_password_.clear();
    ip_.clear();
    policy_.reset();
    stop_provision_timer();
    if (reconnect_timer_ != nullptr) {
        esp_timer_stop(reconnect_timer_);
    }

    esp_err_t err = credentials_.clear();
    state_ = WifiState::NOT_PROVISIONED;
    esp_wifi_disconnect();
    return err;
}

void WifiManager::try_reconnect() {
    if (state_ == WifiState::NOT_PROVISIONED && !has_pending_) {
        return;
    }

    if (has_pending_) {
        // Continua tentando o candidato provisionado (ainda nao persistido).
        (void)apply_and_connect(pending_ssid_, pending_password_);
        return;
    }

    const size_t n = credentials_.count();
    if (n == 0) {
        state_ = WifiState::NOT_PROVISIONED;
        return;
    }

    // Rotaciona entre as redes conhecidas em ordem MRU, respeitando o backoff.
    connect_index_ = (connect_index_ + 1) % n;
    (void)try_connect_known(connect_index_);
}

void WifiManager::schedule_reconnect(bool round_done) {
    // Entre redes da mesma rodada so se espera a troca de AP; o backoff
    // exponencial vale entre rodadas completas.
    const uint32_t delay_ms =
        round_done ? policy_.next_delay_ms() : WifiRoundTracker::kInterNetworkDelayMs;
    if (round_done) {
        ESP_LOGW(TAG, "Wi-Fi desconectado; nova rodada em %u ms (tentativa #%u)",
                 static_cast<unsigned>(delay_ms), static_cast<unsigned>(policy_.attempts()));
    }

    if (reconnect_timer_ == nullptr) {
        esp_timer_create_args_t timer_args = {};
        timer_args.callback = &on_reconnect_timer;
        timer_args.arg = this;
        timer_args.dispatch_method = ESP_TIMER_TASK;
        timer_args.name = "wifi_reconnect";
        if (esp_timer_create(&timer_args, &reconnect_timer_) != ESP_OK) {
            try_reconnect();
            return;
        }
    }
    esp_timer_start_once(reconnect_timer_, static_cast<uint64_t>(delay_ms) * 1000ULL);
}

} // namespace cafey::core
