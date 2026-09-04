#pragma once

#include <cstddef>
#include <cstdint>

#include "event.hpp"
#include "nvs_store.hpp"

namespace cafey::storage {

/**
 * @brief Persists the pending brewing-event queue in NVS as a circular
 * buffer (spec §5.4: "Fila de eventos de preparo com carimbo de tempo, em
 * buffer circular no NVS").
 *
 * When full, push() overwrites the oldest event: the queue favors keeping
 * the most recent activity over guaranteeing delivery of every event
 * generated during an extended offline period (bounded flash usage, KISS).
 */
class EventQueueStore {
public:
    static constexpr size_t kCapacity = 32;

    EventQueueStore();

    /**
     * @brief Opens the underlying NVS namespace and loads any previously
     * persisted queue contents into memory.
     * @return ESP_OK on success, or ESP-IDF error code.
     */
    esp_err_t init();

    /**
     * @brief Enqueues an event and persists the queue immediately. If the
     * queue is full, the oldest event is dropped to make room.
     * @return ESP_OK on success, or ESP-IDF error code from the NVS write.
     */
    esp_err_t push(const Event& event);

    /**
     * @brief Removes and returns the oldest event, persisting the queue
     * immediately.
     * @param out_event Destination for the dequeued event.
     * @return ESP_OK on success, ESP_ERR_INVALID_STATE if the queue is empty,
     * or ESP-IDF error code from the NVS write.
     */
    esp_err_t pop(Event* out_event);

    [[nodiscard]] size_t size() const noexcept { return count_; }
    [[nodiscard]] bool empty() const noexcept { return count_ == 0; }
    [[nodiscard]] bool full() const noexcept { return count_ == kCapacity; }

private:
    struct PersistedLayout {
        uint32_t head; // index of the oldest event
        uint32_t count;
        Event events[kCapacity];
    };

    esp_err_t persist();

    NvsStore nvs_;
    Event events_[kCapacity];
    size_t head_;
    size_t count_;
};

} // namespace cafey::storage
