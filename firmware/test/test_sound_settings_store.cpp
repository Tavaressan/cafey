#include <iostream>

#include "storage/sound_settings_store.hpp"

#define TEST_ASSERT(cond, msg)                                                  \
    do {                                                                       \
        if (!(cond)) {                                                         \
            std::cerr << "FAILED: " << msg << " (" << __FILE__ << ":"          \
                      << __LINE__ << ")" << std::endl;                         \
            return 1;                                                          \
        }                                                                      \
    } while (0)

using cafey::storage::SoundSettingsStore;

int test_defaults_to_sound_on() {
    MockNvs::reset();
    SoundSettingsStore store;
    TEST_ASSERT(store.init() == ESP_OK, "init ok sem chave gravada");
    TEST_ASSERT(!store.muted(), "primeira execucao: som ligado");
    std::cout << "[PASS] test_defaults_to_sound_on" << std::endl;
    return 0;
}

int test_muted_survives_reboot() {
    MockNvs::reset();
    {
        SoundSettingsStore store;
        store.init();
        TEST_ASSERT(store.set_muted(true) == ESP_OK, "grava silencioso");
    }
    SoundSettingsStore after_reboot;
    TEST_ASSERT(after_reboot.init() == ESP_OK, "reabre o namespace");
    TEST_ASSERT(after_reboot.muted(), "silencioso sobrevive a reboot");
    TEST_ASSERT(after_reboot.set_muted(false) == ESP_OK, "reativa o som");
    SoundSettingsStore again;
    again.init();
    TEST_ASSERT(!again.muted(), "som ativado tambem persiste");
    std::cout << "[PASS] test_muted_survives_reboot" << std::endl;
    return 0;
}

int test_same_value_does_not_write_flash() {
    MockNvs::reset();
    SoundSettingsStore store;
    store.init();
    const int commits = MockNvs::commit_call_count;
    TEST_ASSERT(store.set_muted(false) == ESP_OK, "mesmo valor e ok");
    TEST_ASSERT(MockNvs::commit_call_count == commits, "mesmo valor nao grava");
    std::cout << "[PASS] test_same_value_does_not_write_flash" << std::endl;
    return 0;
}

int test_write_failure_keeps_previous_value() {
    MockNvs::reset();
    SoundSettingsStore store;
    store.init();
    MockNvs::set_blob_ret_code = ESP_FAIL;
    TEST_ASSERT(store.set_muted(true) != ESP_OK, "erro do NVS e repassado");
    TEST_ASSERT(!store.muted(), "valor em memoria nao muda se a gravacao falhar");
    std::cout << "[PASS] test_write_failure_keeps_previous_value" << std::endl;
    return 0;
}

int test_set_before_init_is_rejected() {
    MockNvs::reset();
    SoundSettingsStore store;
    TEST_ASSERT(store.set_muted(true) == ESP_ERR_INVALID_STATE, "sem init nao grava");
    std::cout << "[PASS] test_set_before_init_is_rejected" << std::endl;
    return 0;
}

int main() {
    std::cout << "Running Cafey SoundSettingsStore Unit Tests (#311)..." << std::endl;
    if (test_defaults_to_sound_on()) return 1;
    if (test_muted_survives_reboot()) return 1;
    if (test_same_value_does_not_write_flash()) return 1;
    if (test_write_failure_keeps_previous_value()) return 1;
    if (test_set_before_init_is_rejected()) return 1;
    std::cout << "All SoundSettingsStore tests PASSED!" << std::endl;
    return 0;
}
