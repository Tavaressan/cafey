#pragma once

#include <cstddef>
#include <cstdint>

#include "schedule.hpp"
#include "nvs_store.hpp"

namespace cafey::storage {

/**
 * @brief Persists the device's coffee-maker schedules in NVS (spec §5.4:
 * "Agendamentos gravados em NVS — sobrevivem a reboot e queda de energia").
 *
 * Fixed-capacity array persisted as a single blob under one NVS key: simple
 * and sufficient for the expected schedule count (a handful of alarms per
 * device), avoiding a variable-length serialization format (YAGNI).
 */
class ScheduleStore {
public:
    static constexpr size_t kMaxSchedules = 16;

    ScheduleStore();

    /**
     * @brief Opens the underlying NVS namespace and loads any previously
     * persisted schedules into memory.
     * @return ESP_OK on success, or ESP-IDF error code.
     */
    esp_err_t init();

    /**
     * @brief Replaces the full schedule list and persists it immediately.
     * @return ESP_OK on success, ESP_ERR_INVALID_SIZE if `count` exceeds
     * kMaxSchedules, or ESP-IDF error code from the NVS write.
     */
    esp_err_t replace_all(const Schedule* schedules, size_t count);

    [[nodiscard]] size_t count() const noexcept { return count_; }
    [[nodiscard]] const Schedule& at(size_t index) const { return schedules_[index]; }

private:
    struct PersistedLayout {
        uint32_t count;
        Schedule schedules[kMaxSchedules];
    };

    NvsStore nvs_;
    Schedule schedules_[kMaxSchedules];
    size_t count_;
};

} // namespace cafey::storage
