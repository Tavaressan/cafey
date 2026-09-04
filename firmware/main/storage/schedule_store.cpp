#include "schedule_store.hpp"

#include <cstring>

namespace cafey::storage {

namespace {
constexpr const char* kNamespace = "cafey_sched";
constexpr const char* kKey = "list";
} // namespace

ScheduleStore::ScheduleStore()
    : nvs_(kNamespace),
      count_(0) {}

esp_err_t ScheduleStore::init() {
    esp_err_t err = nvs_.init();
    if (err != ESP_OK) {
        return err;
    }

    PersistedLayout layout{};
    err = nvs_.load_blob(kKey, &layout, sizeof(layout));
    if (err == ESP_ERR_NVS_NOT_FOUND) {
        // Nothing persisted yet: start with an empty schedule list.
        count_ = 0;
        return ESP_OK;
    }
    if (err != ESP_OK) {
        return err;
    }

    count_ = layout.count > kMaxSchedules ? kMaxSchedules : layout.count;
    std::memcpy(schedules_, layout.schedules, count_ * sizeof(Schedule));
    return ESP_OK;
}

esp_err_t ScheduleStore::replace_all(const Schedule* schedules, size_t count) {
    if (count > kMaxSchedules) {
        return ESP_ERR_INVALID_SIZE;
    }

    PersistedLayout layout{};
    layout.count = static_cast<uint32_t>(count);
    if (count > 0) {
        std::memcpy(layout.schedules, schedules, count * sizeof(Schedule));
    }

    esp_err_t err = nvs_.save_blob(kKey, &layout, sizeof(layout));
    if (err != ESP_OK) {
        return err;
    }

    count_ = count;
    if (count > 0) {
        std::memcpy(schedules_, schedules, count * sizeof(Schedule));
    }
    return ESP_OK;
}

} // namespace cafey::storage
