#include <cassert>
#include <iostream>
#include <string>

#include "mock_esp_nvs.hpp"
#include "core/mqtt_config_store.hpp"

using cafey::core::MqttConfig;
using cafey::core::MqttConfigStore;

static MqttConfig sample_config() {
    MqttConfig c;
    c.broker_uri = "mqtts://abc123-ats.iot.us-east-1.amazonaws.com:8883";
    c.client_id = "cafey-001";
    c.root_ca_pem = "-----BEGIN CERTIFICATE-----\nROOTCA\n-----END CERTIFICATE-----\n";
    c.device_cert_pem = "-----BEGIN CERTIFICATE-----\nDEVICE\n-----END CERTIFICATE-----\n";
    c.private_key_pem = "-----BEGIN RSA PRIVATE KEY-----\nKEY\n-----END RSA PRIVATE KEY-----\n";
    return c;
}

static void test_has_config_false_when_never_saved() {
    MockNvs::reset();
    MqttConfigStore store;
    assert(!store.has_config());
    std::cout << "OK: test_has_config_false_when_never_saved\n";
}

static void test_save_then_load_roundtrip() {
    MockNvs::reset();
    MqttConfigStore store;
    assert(store.save(sample_config()) == ESP_OK);
    assert(store.has_config());

    MqttConfig loaded;
    assert(store.load(loaded) == ESP_OK);
    assert(loaded.broker_uri == sample_config().broker_uri);
    assert(loaded.client_id == "cafey-001");
    assert(loaded.root_ca_pem == sample_config().root_ca_pem);
    assert(loaded.device_cert_pem == sample_config().device_cert_pem);
    assert(loaded.private_key_pem == sample_config().private_key_pem);
    std::cout << "OK: test_save_then_load_roundtrip\n";
}

static void test_load_without_config_fails() {
    MockNvs::reset();
    MqttConfigStore store;
    MqttConfig loaded;
    assert(store.load(loaded) == ESP_ERR_NVS_NOT_FOUND);
    std::cout << "OK: test_load_without_config_fails\n";
}

int main() {
    test_has_config_false_when_never_saved();
    test_save_then_load_roundtrip();
    test_load_without_config_fails();
    std::cout << "Todos os testes de MqttConfigStore passaram.\n";
    return 0;
}
