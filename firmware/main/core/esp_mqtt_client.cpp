#if defined(ESP_PLATFORM)

#include "core/esp_mqtt_client.hpp"

#include "esp_log.h"
#include "esp_system.h"

namespace cafey::core {

namespace {
const char* TAG = "mqtt";

// A lista retida de `agendamentos` com 16 itens passa de 1 KB (buffer padrao
// do esp-mqtt); acima do buffer o MQTT_EVENT_DATA chega fragmentado (FW-21).
constexpr int kBufferSize = 4096;
} // namespace

EspMqttClient::~EspMqttClient() {
    if (client_ != nullptr) {
        esp_mqtt_client_destroy(client_);
    }
}

esp_err_t EspMqttClient::start(const MqttConfig& config) {
    config_ = config;

    esp_mqtt_client_config_t cfg = {};
    cfg.broker.address.uri = config_.broker_uri.c_str();
    cfg.broker.verification.certificate = config_.root_ca_pem.c_str();
    cfg.credentials.client_id = config_.client_id.c_str();
    cfg.credentials.authentication.certificate = config_.device_cert_pem.c_str();
    cfg.credentials.authentication.key = config_.private_key_pem.c_str();
    cfg.buffer.size = kBufferSize;

    if (!config_.last_will_topic.empty()) {
        cfg.session.last_will.topic = config_.last_will_topic.c_str();
        cfg.session.last_will.msg = config_.last_will_payload.c_str();
        cfg.session.last_will.msg_len = static_cast<int>(config_.last_will_payload.size());
        cfg.session.last_will.qos = kMqttQos1;
        cfg.session.last_will.retain = config_.last_will_retain ? 1 : 0;
    }

    client_ = esp_mqtt_client_init(&cfg);
    if (client_ == nullptr) {
        return ESP_FAIL;
    }

    esp_err_t err = esp_mqtt_client_register_event(
        client_, MQTT_EVENT_ANY, &EspMqttClient::event_handler, this);
    if (err != ESP_OK) {
        return err;
    }

    ESP_LOGI(TAG, "conectando a %s como %s (heap livre %u)", config_.broker_uri.c_str(),
             config_.client_id.c_str(), static_cast<unsigned>(esp_get_free_heap_size()));
    return esp_mqtt_client_start(client_);
}

bool EspMqttClient::publish(const std::string& topic, const std::string& payload,
                            int qos, bool retain) {
    if (client_ == nullptr) {
        return false;
    }
    int msg_id = esp_mqtt_client_publish(client_, topic.c_str(), payload.data(),
                                         static_cast<int>(payload.size()), qos,
                                         retain ? 1 : 0);
    return msg_id >= 0;
}

bool EspMqttClient::subscribe(const std::string& topic, int qos) {
    if (client_ == nullptr) {
        return false;
    }
    return esp_mqtt_client_subscribe(client_, topic.c_str(), qos) >= 0;
}

void EspMqttClient::event_handler(void* arg, esp_event_base_t /*base*/,
                                  int32_t event_id, void* event_data) {
    auto* self = static_cast<EspMqttClient*>(arg);
    auto* event = static_cast<esp_mqtt_event_handle_t>(event_data);
    switch (static_cast<esp_mqtt_event_id_t>(event_id)) {
        case MQTT_EVENT_CONNECTED:
            self->connected_ = true;
            ESP_LOGI(TAG, "conectado ao broker (heap livre %u)",
                     static_cast<unsigned>(esp_get_free_heap_size()));
            if (self->on_connected_) self->on_connected_();
            break;
        case MQTT_EVENT_DISCONNECTED:
            self->connected_ = false;
            ESP_LOGW(TAG, "desconectado do broker");
            if (self->on_disconnected_) self->on_disconnected_();
            break;
        case MQTT_EVENT_SUBSCRIBED:
            ESP_LOGI(TAG, "assinatura aceita (msg_id=%d)", event->msg_id);
            break;
        case MQTT_EVENT_DATA:
            if (event->data_len != event->total_data_len) {
                // Fragmentado mesmo com kBufferSize: descarta em vez de
                // entregar um JSON pela metade ao dominio.
                ESP_LOGW(TAG, "mensagem de %d bytes maior que o buffer; descartada",
                         event->total_data_len);
                break;
            }
            if (self->on_message_) {
                self->on_message_(std::string(event->topic, event->topic_len),
                                  std::string(event->data, event->data_len));
            }
            break;
        case MQTT_EVENT_ERROR:
            // Diagnostico de bancada: distingue falha de TLS/certificado de
            // recusa do broker (politica/clientId).
            if (event->error_handle != nullptr) {
                ESP_LOGE(TAG, "erro tipo=%d tls_err=0x%x tls_stack=0x%x connack=%d errno=%d",
                         event->error_handle->error_type,
                         event->error_handle->esp_tls_last_esp_err,
                         event->error_handle->esp_tls_stack_err,
                         event->error_handle->connect_return_code,
                         event->error_handle->esp_transport_sock_errno);
            }
            break;
        default:
            break;
    }
}

} // namespace cafey::core

#endif // ESP_PLATFORM
