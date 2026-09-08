#include <cassert>
#include <iostream>

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
using cafey::storage::EventQueueStore;

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

    Event e1{100, 130, EventOrigin::AGENDAMENTO};
    Event e2{200, 210, EventOrigin::BOTAO};

    TEST_ASSERT(store.push(e1) == ESP_OK, "push(e1) should succeed");
    TEST_ASSERT(store.push(e2) == ESP_OK, "push(e2) should succeed");
    TEST_ASSERT(store.size() == 2, "size() should be 2 after two pushes");

    Event out{};
    TEST_ASSERT(store.pop(&out) == ESP_OK, "pop() should succeed");
    TEST_ASSERT(out.timestamp_inicio == 100, "first popped event must be e1 (FIFO)");
    TEST_ASSERT(out.origem == EventOrigin::AGENDAMENTO, "origin must match e1");

    TEST_ASSERT(store.pop(&out) == ESP_OK, "pop() should succeed");
    TEST_ASSERT(out.timestamp_inicio == 200, "second popped event must be e2 (FIFO)");

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
        Event e{i, i + 1, EventOrigin::APP};
        TEST_ASSERT(store.push(e) == ESP_OK, "push() should succeed while filling capacity");
    }
    TEST_ASSERT(store.full(), "queue should report full at capacity");

    // One more push should overwrite the oldest (timestamp_inicio == 0).
    Event overflow{9999, 10000, EventOrigin::AGENDAMENTO};
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
        store.push(Event{42, 50, EventOrigin::BOTAO});
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
    }

    std::cout << "[PASS] test_queue_survives_reboot" << std::endl;
    return 0;
}

int test_push_fails_if_nvs_persist_fails() {
    MockNvs::reset();
    EventQueueStore store;
    store.init();

    Event e1{100, 130, EventOrigin::AGENDAMENTO};
    TEST_ASSERT(store.push(e1) == ESP_OK, "first push() should succeed");
    TEST_ASSERT(store.size() == 1, "size() should be 1 after first push");

    // Inject NVS write failure for next persist().
    Event e2{200, 210, EventOrigin::BOTAO};
    MockNvs::set_blob_ret_code = ESP_FAIL;
    esp_err_t result = store.push(e2);
    TEST_ASSERT(result == ESP_FAIL, "push() must return the persist() error");

    // CRITICAL: RAM must NOT advance if persist() fails.
    // With the fix, count_ should still be 1 (not incremented).
    // Without the fix, count_ would be 2 (already incremented before persist failed).
    TEST_ASSERT(store.size() == 1, "size() must NOT advance when persist() fails; must stay at 1");

    // Verify NVS still has only 1 event (not 2).
    // Create a new store instance and verify it reloads only 1 event.
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

    Event e1{100, 130, EventOrigin::AGENDAMENTO};
    TEST_ASSERT(store.push(e1) == ESP_OK, "push(e1) should succeed");
    TEST_ASSERT(store.size() == 1, "size() should be 1 after push");

    // Inject NVS write failure for pop's persist().
    MockNvs::set_blob_ret_code = ESP_FAIL;
    Event out{};
    esp_err_t result = store.pop(&out);
    TEST_ASSERT(result == ESP_FAIL, "pop() must return the persist() error");

    // CRITICAL: RAM must NOT advance if persist() fails.
    // With the fix, count_ should still be 1 (not decremented).
    // Without the fix, count_ would be 0 (already decremented before persist failed).
    TEST_ASSERT(store.size() == 1, "size() must NOT advance when persist() fails; must stay at 1");

    // Verify NVS still has 1 event.
    MockNvs::set_blob_ret_code = ESP_OK; // restore normal operation
    {
        EventQueueStore store2;
        TEST_ASSERT(store2.init() == ESP_OK, "second instance init() should succeed");
        TEST_ASSERT(store2.size() == 1, "second instance must reload 1 event from NVS (pop was never persisted)");
    }

    std::cout << "[PASS] test_pop_fails_if_nvs_persist_fails" << std::endl;
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

    std::cout << "All 7 EventQueueStore tests PASSED!" << std::endl;
    return 0;
}
