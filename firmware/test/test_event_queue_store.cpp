#include <cassert>
#include <iostream>
#include <vector>

#include "mock_esp_nvs.hpp"
#include "event_queue_store.hpp"

#define TEST_ASSERT(cond, msg) \
    do { \
        if (!(cond)) { \
            std::cerr << "FAILED: " << msg << " (" << __FILE__ << ":" << __LINE__ << ")" << std::endl; \
            return 1; \
        } \
    } while (0)

using cafey::storage::Event;
using cafey::storage::EventOrigin;
using cafey::storage::EventResult;
using cafey::storage::EventQueueStore;
using cafey::storage::make_brew_event;

int test_starts_empty_when_nothing_persisted() {
    MockNvs::reset();
    EventQueueStore store;
    TEST_ASSERT(store.init() == ESP_OK, "init() should succeed with no prior data");
    TEST_ASSERT(store.empty(), "queue should start empty");
    TEST_ASSERT(store.size() == 0, "size() should be 0");

    std::cout << "[PASS] test_starts_empty_when_nothing_persisted" << std::endl;
    return 0;
}

int test_push_pop_is_fifo() {
    MockNvs::reset();
    EventQueueStore store;
    store.init();

    Event e1 = make_brew_event("id:1", EventOrigin::AGENDAMENTO, EventResult::CONCLUIDO, 30, 100, 130, true);
    Event e2 = make_brew_event("id:2", EventOrigin::BOTAO, EventResult::CANCELADO, 10, 200, 210, true);

    TEST_ASSERT(store.push(e1) == ESP_OK, "push(e1) should succeed");
    TEST_ASSERT(store.push(e2) == ESP_OK, "push(e2) should succeed");
    TEST_ASSERT(store.size() == 2, "size() should be 2 after two pushes");

    Event out{};
    TEST_ASSERT(store.pop(&out) == ESP_OK, "pop() should succeed");
    TEST_ASSERT(out.timestamp_inicio == 100, "first popped event must be e1 (FIFO)");
    TEST_ASSERT(out.origem == EventOrigin::AGENDAMENTO, "origin must match e1");
    TEST_ASSERT(out.resultado == EventResult::CONCLUIDO, "result must match e1");
    TEST_ASSERT(out.duracao_s == 30, "duration must match e1");
    TEST_ASSERT(std::string(out.evento_id) == "id:1", "evento_id must match e1");

    TEST_ASSERT(store.pop(&out) == ESP_OK, "pop() should succeed");
    TEST_ASSERT(out.timestamp_inicio == 200, "second popped event must be e2 (FIFO)");
    TEST_ASSERT(out.resultado == EventResult::CANCELADO, "result must match e2");

    TEST_ASSERT(store.empty(), "queue should be empty after popping both events");

    std::cout << "[PASS] test_push_pop_is_fifo" << std::endl;
    return 0;
}

int test_pop_on_empty_queue_fails() {
    MockNvs::reset();
    EventQueueStore store;
    store.init();

    TEST_ASSERT(store.pop(nullptr) == ESP_ERR_INVALID_STATE, "pop() on empty queue must return ESP_ERR_INVALID_STATE");

    std::cout << "[PASS] test_pop_on_empty_queue_fails" << std::endl;
    return 0;
}

int test_push_beyond_capacity_overwrites_oldest() {
    MockNvs::reset();
    EventQueueStore store;
    store.init();

    for (uint32_t i = 0; i < EventQueueStore::kCapacity; ++i) {
        Event e = make_brew_event("", EventOrigin::APP, EventResult::CONCLUIDO, 1, i, i + 1, true);
        TEST_ASSERT(store.push(e) == ESP_OK, "push() should succeed while filling capacity");
    }
    TEST_ASSERT(store.full(), "queue should report full at capacity");

    // One more push should overwrite the oldest (timestamp_inicio == 0).
    Event overflow = make_brew_event("ovf", EventOrigin::AGENDAMENTO, EventResult::CONCLUIDO, 1, 9999, 10000, true);
    TEST_ASSERT(store.push(overflow) == ESP_OK, "push() beyond capacity should still succeed");
    TEST_ASSERT(store.size() == EventQueueStore::kCapacity, "size() must stay bounded at kCapacity");

    Event out{};
    TEST_ASSERT(store.pop(&out) == ESP_OK, "pop() should succeed");
    TEST_ASSERT(out.timestamp_inicio == 1, "oldest event (timestamp 0) must have been dropped");

    std::cout << "[PASS] test_push_beyond_capacity_overwrites_oldest" << std::endl;
    return 0;
}

int test_queue_survives_reboot() {
    MockNvs::reset();
    {
        EventQueueStore store;
        store.init();
        store.push(make_brew_event("boot:42", EventOrigin::BOTAO, EventResult::CONCLUIDO, 8, 42, 50, true));
    }

    // Simulate reboot: new instance re-reads NVS.
    {
        EventQueueStore store;
        TEST_ASSERT(store.init() == ESP_OK, "init() after reboot should succeed");
        TEST_ASSERT(store.size() == 1, "queue contents must survive reboot");

        Event out{};
        TEST_ASSERT(store.pop(&out) == ESP_OK, "pop() after reboot should succeed");
        TEST_ASSERT(out.timestamp_inicio == 42, "event data must survive reboot intact");
        TEST_ASSERT(out.origem == EventOrigin::BOTAO, "event origin must survive reboot intact");
        TEST_ASSERT(std::string(out.evento_id) == "boot:42", "evento_id must survive reboot");
    }

    std::cout << "[PASS] test_queue_survives_reboot" << std::endl;
    return 0;
}

int test_push_fails_if_nvs_persist_fails() {
    MockNvs::reset();
    EventQueueStore store;
    store.init();

    Event e1 = make_brew_event("1", EventOrigin::AGENDAMENTO, EventResult::CONCLUIDO, 30, 100, 130, true);
    TEST_ASSERT(store.push(e1) == ESP_OK, "first push() should succeed");
    TEST_ASSERT(store.size() == 1, "size() should be 1 after first push");

    // Inject NVS write failure for next persist().
    Event e2 = make_brew_event("2", EventOrigin::BOTAO, EventResult::CONCLUIDO, 10, 200, 210, true);
    MockNvs::set_blob_ret_code = ESP_FAIL;
    esp_err_t result = store.push(e2);
    TEST_ASSERT(result == ESP_FAIL, "push() must return the persist() error");

    TEST_ASSERT(store.size() == 1, "size() must NOT advance when persist() fails; must stay at 1");

    MockNvs::set_blob_ret_code = ESP_OK; // restore normal operation
    {
        EventQueueStore store2;
        TEST_ASSERT(store2.init() == ESP_OK, "second instance init() should succeed");
        TEST_ASSERT(store2.size() == 1, "second instance must reload only 1 event from NVS");

        Event out{};
        TEST_ASSERT(store2.pop(&out) == ESP_OK, "pop() on second instance should succeed");
        TEST_ASSERT(out.timestamp_inicio == 100, "event in NVS must be the original e1, not e2");
    }

    std::cout << "[PASS] test_push_fails_if_nvs_persist_fails" << std::endl;
    return 0;
}

int test_pop_fails_if_nvs_persist_fails() {
    MockNvs::reset();
    EventQueueStore store;
    store.init();

    Event e1 = make_brew_event("1", EventOrigin::AGENDAMENTO, EventResult::CONCLUIDO, 30, 100, 130, true);
    TEST_ASSERT(store.push(e1) == ESP_OK, "push(e1) should succeed");
    TEST_ASSERT(store.size() == 1, "size() should be 1 after push");

    MockNvs::set_blob_ret_code = ESP_FAIL;
    Event out{};
    esp_err_t result = store.pop(&out);
    TEST_ASSERT(result == ESP_FAIL, "pop() must return the persist() error");

    TEST_ASSERT(store.size() == 1, "size() must NOT advance when persist() fails; must stay at 1");

    MockNvs::set_blob_ret_code = ESP_OK; // restore normal operation
    {
        EventQueueStore store2;
        TEST_ASSERT(store2.init() == ESP_OK, "second instance init() should succeed");
        TEST_ASSERT(store2.size() == 1, "second instance must reload 1 event from NVS (pop was never persisted)");
    }

    std::cout << "[PASS] test_pop_fails_if_nvs_persist_fails" << std::endl;
    return 0;
}

namespace {
struct LegacyEventV1 {
    uint32_t timestamp_inicio;
    uint32_t timestamp_fim;
    EventOrigin origem;
    bool horario_provisorio;
};
struct LegacyPersistedLayoutV1 {
    uint32_t magic;
    uint16_t schema_version;
    uint16_t reserved;
    uint32_t head;
    uint32_t count;
    LegacyEventV1 events[EventQueueStore::kCapacity];
};
struct LegacyPersistedLayoutV0 {
    uint32_t head;
    uint32_t count;
    LegacyEventV1 events[EventQueueStore::kCapacity];
};

void write_blob(const char* key, const void* data, size_t size) {
    nvs_handle_t handle = 0;
    nvs_open("cafey_evtq", NVS_READWRITE, &handle);
    nvs_set_blob(handle, key, data, size);
    nvs_commit(handle);
}
} // namespace

// FW-19 / FW-22: migracao de V0 (392 bytes) para V2 (1296 bytes).
int test_legacy_blob_v0_is_migrated() {
    MockNvs::reset();

    LegacyPersistedLayoutV0 legacy{};
    legacy.head = 0;
    legacy.count = 3;
    legacy.events[0] = {10, 20, EventOrigin::APP, false};
    legacy.events[1] = {30, 40, EventOrigin::AGENDAMENTO, true};
    legacy.events[2] = {50, 60, EventOrigin::BOTAO, true};
    write_blob("queue", &legacy, sizeof(legacy));

    const bool expected_provisorio[3] = {false, true, true};

    {
        EventQueueStore store;
        TEST_ASSERT(store.init() == ESP_OK, "init() must migrate the V0 blob");
        TEST_ASSERT(store.size() == 3, "all 3 legacy events must survive migration");
        for (size_t i = 0; i < 3; ++i) {
            Event out{};
            TEST_ASSERT(store.at(i, &out) == ESP_OK, "at() should succeed");
            TEST_ASSERT(out.horario_provisorio == expected_provisorio[i],
                        "horario_provisorio must be preserved verbatim through migration");
            TEST_ASSERT(out.resultado == EventResult::CONCLUIDO, "resultado default CONCLUIDO");
            TEST_ASSERT(out.duracao_s == 10, "duracao calculada de fim - inicio");
        }
    }

    // Segundo init() deve ler direto do layout novo V2 regravado pela migração.
    {
        EventQueueStore store;
        TEST_ASSERT(store.init() == ESP_OK, "second init() reads the rewritten V2 layout");
        TEST_ASSERT(store.size() == 3, "rewritten blob keeps the 3 events");
        Event out{};
        TEST_ASSERT(store.pop(&out) == ESP_OK, "pop() should succeed");
        TEST_ASSERT(out.timestamp_inicio == 10, "FIFO order preserved after migration");
        TEST_ASSERT(out.origem == EventOrigin::APP, "origin preserved after migration");
    }

    std::cout << "[PASS] test_legacy_blob_v0_is_migrated" << std::endl;
    return 0;
}

// FW-22: migracao de V1 (400 bytes, schema 1) para V2 (1296 bytes, schema 2).
int test_legacy_blob_v1_is_migrated() {
    MockNvs::reset();

    LegacyPersistedLayoutV1 v1{};
    v1.magic = 0x51464143; // 'CAFQ'
    v1.schema_version = 1;
    v1.head = 0;
    v1.count = 2;
    v1.events[0] = {100, 150, EventOrigin::BOTAO, false};
    v1.events[1] = {200, 300, EventOrigin::APP, true};
    write_blob("queue", &v1, sizeof(v1));

    {
        EventQueueStore store;
        TEST_ASSERT(store.init() == ESP_OK, "init() must migrate V1 blob to V2");
        TEST_ASSERT(store.size() == 2, "all 2 V1 events must survive migration");
        Event out{};
        TEST_ASSERT(store.at(0, &out) == ESP_OK, "read first event");
        TEST_ASSERT(out.timestamp_inicio == 100, "timestamp_inicio preserved");
        TEST_ASSERT(out.duracao_s == 50, "duracao_s calculated 150-100");
        TEST_ASSERT(out.resultado == EventResult::CONCLUIDO, "resultado CONCLUIDO");
        TEST_ASSERT(!out.horario_provisorio, "horario_provisorio false");

        TEST_ASSERT(store.at(1, &out) == ESP_OK, "read second event");
        TEST_ASSERT(out.duracao_s == 100, "duracao_s calculated 300-200");
        TEST_ASSERT(out.horario_provisorio, "horario_provisorio true");
    }

    std::cout << "[PASS] test_legacy_blob_v1_is_migrated" << std::endl;
    return 0;
}

// FW-19: blob de tamanho totalmente desconhecido -> fila vazia, sem travar.
int test_unknown_blob_is_treated_as_empty() {
    MockNvs::reset();

    std::vector<uint8_t> garbage(123, 0x5A);
    write_blob("queue", garbage.data(), garbage.size());

    EventQueueStore store;
    TEST_ASSERT(store.init() == ESP_OK, "init() must recover from unknown blob");
    TEST_ASSERT(store.size() == 0, "queue must be empty after discarding unknown blob");

    std::cout << "[PASS] test_unknown_blob_is_treated_as_empty" << std::endl;
    return 0;
}

int main() {
    std::cout << "Running Cafey EventQueueStore Unit Tests..." << std::endl;

    if (test_starts_empty_when_nothing_persisted()) return 1;
    if (test_push_pop_is_fifo()) return 1;
    if (test_pop_on_empty_queue_fails()) return 1;
    if (test_push_beyond_capacity_overwrites_oldest()) return 1;
    if (test_queue_survives_reboot()) return 1;
    if (test_push_fails_if_nvs_persist_fails()) return 1;
    if (test_pop_fails_if_nvs_persist_fails()) return 1;
    if (test_legacy_blob_v0_is_migrated()) return 1;
    if (test_legacy_blob_v1_is_migrated()) return 1;
    if (test_unknown_blob_is_treated_as_empty()) return 1;

    std::cout << "All EventQueueStore tests PASSED!" << std::endl;
    return 0;
}
