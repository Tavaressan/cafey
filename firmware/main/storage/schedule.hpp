#pragma once

#include <algorithm>
#include <cstddef>
#include <cstdint>

namespace cafey::storage {

/**
 * @brief A single coffee-maker schedule (spec §7.2 table `agendamentos`).
 *
 * Mirrors the backend row, minus the fields that only make sense
 * server-side (dispositivo_id, criado_por). `id` matches the backend UUID
 * so that updates coming down from the cloud can be matched and replaced.
 */
struct Schedule {
    static constexpr size_t kIdLength = 36; // UUID string, no null terminator stored

    char id[kIdLength] = {0};
    uint8_t hour = 0;          // 0-23
    uint8_t minute = 0;        // 0-59
    uint8_t days_of_week = 0;  // bitmask: bit0=domingo ... bit6=sabado
    bool active = false;

    bool operator==(const Schedule& other) const {
        return hour == other.hour &&
               minute == other.minute &&
               days_of_week == other.days_of_week &&
               active == other.active &&
               std::equal(id, id + kIdLength, other.id);
    }
};

} // namespace cafey::storage
