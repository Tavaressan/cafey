#include <cassert>
#include <cstring>
#include <iostream>

#include "mock_esp_nvs.hpp"
#include "schedule_store.hpp"

#define TEST_ASSERT(cond, msg) \
    do { \
        if (!(cond)) { \
            std::cerr << "FAILED: " << msg << " (" << __FILE__ << ":" << __LINE__ << ")" << std::endl; \
            return 1; \
        } \
    } while (0)

using cafey::storage::Schedule;
using cafey::storage::ScheduleStore;

namespace {
Schedule make_schedule(const char* id, uint8_t hour, uint8_t minute, uint8_t days, bool active) {
    Schedule s{};
    std::strncpy(s.id, id, Schedule::kIdLength);
    s.hour = hour;
    s.minute = minute;
    s.days_of_week = days;
    s.active = active;
    return s;
}
} // namespace

int test_starts_empty_when_nothing_persisted() {
    MockNvs::reset();
    ScheduleStore store;
    TEST_ASSERT(store.init() == ESP_OK, "init() should succeed with no prior data");
    TEST_ASSERT(store.count() == 0, "store should start empty");

    std::cout << "[PASS] test_starts_empty_when_nothing_persisted" << std::endl;
    return 0;
}

int test_replace_all_persists_and_reloads() {
    MockNvs::reset();
    Schedule schedules[2] = {
        make_schedule("11111111-1111-1111-1111-111111111111", 7, 30, 0b0111110, true),
        make_schedule("22222222-2222-2222-2222-222222222222", 18, 0, 0b1000001, false),
    };

    {
        ScheduleStore store;
        TEST_ASSERT(store.init() == ESP_OK, "init() should succeed");
        TEST_ASSERT(store.replace_all(schedules, 2) == ESP_OK, "replace_all() should succeed");
        TEST_ASSERT(store.count() == 2, "count() should reflect 2 schedules");
    }

    // Simulate reboot: new instance re-reads NVS.
    {
        ScheduleStore store;
        TEST_ASSERT(store.init() == ESP_OK, "init() after reboot should succeed");
        TEST_ASSERT(store.count() == 2, "schedules must survive reboot");
        TEST_ASSERT(store.at(0) == schedules[0], "first schedule must match after reload");
        TEST_ASSERT(store.at(1) == schedules[1], "second schedule must match after reload");
    }

    std::cout << "[PASS] test_replace_all_persists_and_reloads" << std::endl;
    return 0;
}

int test_replace_all_rejects_overflow() {
    MockNvs::reset();
    ScheduleStore store;
    store.init();

    Schedule schedules[ScheduleStore::kMaxSchedules + 1] = {};
    TEST_ASSERT(store.replace_all(schedules, ScheduleStore::kMaxSchedules + 1) == ESP_ERR_INVALID_SIZE,
                "replace_all() must reject counts above kMaxSchedules");

    std::cout << "[PASS] test_replace_all_rejects_overflow" << std::endl;
    return 0;
}

int test_replace_all_with_empty_list_clears_schedules() {
    MockNvs::reset();
    Schedule schedules[1] = {make_schedule("11111111-1111-1111-1111-111111111111", 6, 0, 0x7f, true)};

    ScheduleStore store;
    store.init();
    store.replace_all(schedules, 1);
    TEST_ASSERT(store.count() == 1, "sanity: one schedule persisted");

    TEST_ASSERT(store.replace_all(nullptr, 0) == ESP_OK, "replace_all() with 0 count should succeed");
    TEST_ASSERT(store.count() == 0, "count() should be 0 after clearing");

    ScheduleStore reloaded;
    reloaded.init();
    TEST_ASSERT(reloaded.count() == 0, "cleared state must survive reboot");

    std::cout << "[PASS] test_replace_all_with_empty_list_clears_schedules" << std::endl;
    return 0;
}

int main() {
    std::cout << "Running Cafey ScheduleStore Unit Tests..." << std::endl;

    if (test_starts_empty_when_nothing_persisted()) return 1;
    if (test_replace_all_persists_and_reloads()) return 1;
    if (test_replace_all_rejects_overflow()) return 1;
    if (test_replace_all_with_empty_list_clears_schedules()) return 1;

    std::cout << "All 4 ScheduleStore tests PASSED!" << std::endl;
    return 0;
}
