#include <cassert>
#include <iostream>
#include <string>
#include "mock_esp_nvs.hpp"
#include "wifi_credentials.hpp"

using cafey::core::WifiCredentialsStore;

static void test_has_credentials_false_when_never_saved() {
    MockNvs::reset();
    WifiCredentialsStore store;
    assert(!store.has_credentials());
    std::cout << "OK: test_has_credentials_false_when_never_saved\n";
}

static void test_save_then_load_roundtrip() {
    MockNvs::reset();
    WifiCredentialsStore store;
    esp_err_t err = store.save("MinhaRedeWifi", "senha-super-secreta");
    assert(err == ESP_OK);
    assert(store.has_credentials());

    std::string ssid, password;
    err = store.load(ssid, password);
    assert(err == ESP_OK);
    assert(ssid == "MinhaRedeWifi");
    assert(password == "senha-super-secreta");
    std::cout << "OK: test_save_then_load_roundtrip\n";
}

static void test_load_without_saved_credentials_fails() {
    MockNvs::reset();
    WifiCredentialsStore store;
    std::string ssid, password;
    esp_err_t err = store.load(ssid, password);
    assert(err == ESP_ERR_NVS_NOT_FOUND);
    std::cout << "OK: test_load_without_saved_credentials_fails\n";
}

static void test_save_overwrites_previous_credentials() {
    MockNvs::reset();
    WifiCredentialsStore store;
    store.save("RedeAntiga", "senhaAntiga");
    store.save("RedeNova", "senhaNova");

    std::string ssid, password;
    store.load(ssid, password);
    assert(ssid == "RedeNova");
    assert(password == "senhaNova");
    std::cout << "OK: test_save_overwrites_previous_credentials\n";
}

static void test_clear_removes_credentials() {
    MockNvs::reset();
    WifiCredentialsStore store;
    store.save("Rede", "senha");
    assert(store.has_credentials());

    esp_err_t err = store.clear();
    assert(err == ESP_OK);
    assert(!store.has_credentials());
    std::cout << "OK: test_clear_removes_credentials\n";
}

int main() {
    test_has_credentials_false_when_never_saved();
    test_save_then_load_roundtrip();
    test_load_without_saved_credentials_fails();
    test_save_overwrites_previous_credentials();
    test_clear_removes_credentials();
    std::cout << "Todos os testes de WifiCredentialsStore passaram.\n";
    return 0;
}
