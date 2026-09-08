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
    // Pré-calcula novo estado sem mutar RAM.
    size_t new_head = head_;
    size_t new_count = count_;

    if (full()) {
        // Descarta o evento mais antigo para fazer espaço.
        new_head = (new_head + 1) % kCapacity;
        new_count--;
    }

    size_t tail = (new_head + new_count) % kCapacity;
    new_count++;

    // Constrói PersistedLayout com novo estado.
    PersistedLayout layout{};
    layout.head = static_cast<uint32_t>(new_head);
    layout.count = static_cast<uint32_t>(new_count);
    std::memcpy(layout.events, events_, sizeof(events_));
    layout.events[tail] = event;

    // Persiste PRIMEIRO.
    esp_err_t err = nvs_.save_blob(kKey, &layout, sizeof(layout));
    if (err != ESP_OK) {
        return err;  // RAM não é mutado se persist falhar.
    }

    // Aplica mutações em RAM apenas após persist bem-sucedido.
    head_ = new_head;
    count_ = new_count;
    events_[tail] = event;

    return ESP_OK;
}

esp_err_t EventQueueStore::pop(Event* out_event) {
    if (empty()) {
        return ESP_ERR_INVALID_STATE;
    }

    if (out_event != nullptr) {
        *out_event = events_[head_];
    }

    // Pré-calcula novo estado sem mutar RAM.
    size_t new_head = (head_ + 1) % kCapacity;
    size_t new_count = count_ - 1;

    // Constrói PersistedLayout com novo estado.
    PersistedLayout layout{};
    layout.head = static_cast<uint32_t>(new_head);
    layout.count = static_cast<uint32_t>(new_count);
    std::memcpy(layout.events, events_, sizeof(events_));

    // Persiste PRIMEIRO.
    esp_err_t err = nvs_.save_blob(kKey, &layout, sizeof(layout));
    if (err != ESP_OK) {
        return err;  // RAM não é mutado se persist falhar.
    }

    // Aplica mutações em RAM apenas após persist bem-sucedido.
    head_ = new_head;
    count_ = new_count;

    return ESP_OK;
}

} // namespace cafey::storage
