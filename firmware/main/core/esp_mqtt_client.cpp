#if defined(ESP_PLATFORM)

#include "core/esp_mqtt_client.hpp"

#include "esp_log.h"

namespace cafey::core {

namespace {
const char* TAG = "mqtt";
} // namespace

EspMqttClient::~EspMqttClient() {
    if (client_ != nullptr) {
        esp_mqtt_client_destroy(client_);
    }
}

esp_err_t EspMqttClient::start(const MqttConfig& config) {
    esp_mqtt_client_config_t cfg = {};
    cfg.broker.address.uri = config.broker_uri.c_str();
    cfg.broker.verification.certificate = config.root_ca_pem.c_str();
    cfg.credentials.client_id = config.client_id.c_str();
    cfg.credentials.authentication.certificate = config.device_cert_pem.c_str();
    cfg.credentials.authentication.key = config.private_key_pem.c_str();

    if (!config.last_will_topic.empty()) {
        cfg.session.last_will.topic = config.last_will_topic.c_str();
        cfg.session.last_will.msg = config.last_will_payload.c_str();
        cfg.session.last_will.msg_len = static_cast<int>(config.last_will_payload.size());
        cfg.session.last_will.qos = kMqttQos1;
        cfg.session.last_will.retain = config.last_will_retain ? 1 : 0;
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
                                  int32_t event_id, void* /*event_data*/) {
    auto* self = static_cast<EspMqttClient*>(arg);
    switch (static_cast<esp_mqtt_event_id_t>(event_id)) {
        case MQTT_EVENT_CONNECTED:
            self->connected_ = true;
            ESP_LOGI(TAG, "conectado ao broker");
            break;
        case MQTT_EVENT_DISCONNECTED:
            self->connected_ = false;
            ESP_LOGW(TAG, "desconectado do broker");
            break;
        default:
            break;
    }
}

} // namespace cafey::core

#endif // ESP_PLATFORM
