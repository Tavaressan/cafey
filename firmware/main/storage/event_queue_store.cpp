#include "event_queue_store.hpp"

#include <cstring>

namespace cafey::storage {

namespace {
constexpr const char* kNamespace = "cafey_evtq";
constexpr const char* kKey = "queue";
} // namespace

EventQueueStore::EventQueueStore()
    : nvs_(kNamespace),
      head_(0),
      count_(0) {}

esp_err_t EventQueueStore::init() {
    esp_err_t err = nvs_.init();
    if (err != ESP_OK) {
        return err;
    }

    PersistedLayout layout{};
    err = nvs_.load_blob(kKey, &layout, sizeof(layout));
    if (err == ESP_ERR_NVS_NOT_FOUND) {
        head_ = 0;
        count_ = 0;
        return ESP_OK;
    }
    if (err != ESP_OK) {
        return err;
    }

    head_ = layout.head % kCapacity;
    count_ = layout.count > kCapacity ? kCapacity : layout.count;
    std::memcpy(events_, layout.events, sizeof(events_));
    return ESP_OK;
}

esp_err_t EventQueueStore::persist() {
    PersistedLayout layout{};
    layout.head = static_cast<uint32_t>(head_);
    layout.count = static_cast<uint32_t>(count_);
    std::memcpy(layout.events, events_, sizeof(events_));
    return nvs_.save_blob(kKey, &layout, sizeof(layout));
}

esp_err_t EventQueueStore::push(const Event& event) {
    if (full()) {
        // Drop the oldest event to make room, per the ring-buffer contract.
        head_ = (head_ + 1) % kCapacity;
        count_--;
    }

    size_t tail = (head_ + count_) % kCapacity;
    events_[tail] = event;
    count_++;

    return persist();
}

esp_err_t EventQueueStore::pop(Event* out_event) {
    if (empty()) {
        return ESP_ERR_INVALID_STATE;
    }

    if (out_event != nullptr) {
        *out_event = events_[head_];
    }
    head_ = (head_ + 1) % kCapacity;
    count_--;

    return persist();
}

} // namespace cafey::storage
