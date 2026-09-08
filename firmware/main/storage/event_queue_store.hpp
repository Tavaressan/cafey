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

    /**
     * @brief Copies the oldest event without removing it, so a caller can try to
     * deliver it and only pop() after confirmed success (FW-16 drain).
     * @return ESP_OK on success, ESP_ERR_INVALID_STATE if the queue is empty.
     */
    esp_err_t front(Event* out_event) const;

    /**
     * @brief Copies the event at logical position `index` (0 = oldest) without
     * removing it, so the BLE proxy (FW-18) can enumerate the whole pending
     * queue for the phone to relay.
     * @return ESP_OK on success, ESP_ERR_INVALID_STATE if `index` is out of range.
     */
    esp_err_t at(size_t index, Event* out_event) const;

    /**
     * @brief Removes every queued event whose `timestamp_inicio` is listed in
     * `confirmed_inicios` (FW-18, spec §6.5: the phone only confirms the subset
     * the backend actually accepted; `timestamp_inicio` is the backend's
     * dedup key). FIFO order of the survivors is preserved. Persists before
     * mutating RAM.
     * @return number of events removed (0 if none matched or the write failed).
     */
    size_t remove_confirmed(const uint32_t* confirmed_inicios, size_t count);

    [[nodiscard]] size_t size() const noexcept { return count_; }
    [[nodiscard]] bool empty() const noexcept { return count_ == 0; }
    [[nodiscard]] bool full() const noexcept { return count_ == kCapacity; }

private:
    // Prefixo de schema gravado no início do blob (FW-19).
    static constexpr uint32_t kEventQueueMagic = 0x51464143;  // 'CAFQ' LE
    static constexpr uint16_t kEventQueueSchemaVersion = 1;

    struct PersistedLayout {
        uint32_t magic;
        uint16_t schema_version;
        uint16_t reserved;
        uint32_t head; // index of the oldest event
        uint32_t count;
        Event events[kCapacity];
    };

    // Layout gravado pelo firmware anterior à PR #124: sem prefixo de schema e
    // com `Event` sem o campo `horario_provisorio`. Usado só para migrar blobs
    // legados na primeira inicialização após a atualização (FW-19).
    struct LegacyEventV0 {
        uint32_t timestamp_inicio;
        uint32_t timestamp_fim;
        EventOrigin origem;
    };
    struct LegacyPersistedLayoutV0 {
        uint32_t head;
        uint32_t count;
        LegacyEventV0 events[kCapacity];
    };

    esp_err_t persist();

    NvsStore nvs_;
    Event events_[kCapacity];
    size_t head_;
    size_t count_;
};

} // namespace cafey::storage
