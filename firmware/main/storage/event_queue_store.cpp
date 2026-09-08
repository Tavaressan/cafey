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

    size_t stored_size = 0;
    err = nvs_.blob_size(kKey, &stored_size);
    if (err == ESP_ERR_NVS_NOT_FOUND) {
        head_ = 0;
        count_ = 0;
        return ESP_OK;
    }
    if (err != ESP_OK) {
        return err;
    }

    if (stored_size == sizeof(PersistedLayout)) {
        PersistedLayout layout{};
        err = nvs_.load_blob(kKey, &layout, sizeof(layout));
        if (err != ESP_OK) {
            return err;
        }
        if (layout.magic != kEventQueueMagic ||
            layout.schema_version != kEventQueueSchemaVersion) {
            ESP_LOGW("EventQueueStore", "magic/schema desconhecido, fila zerada");
            head_ = 0;
            count_ = 0;
            return ESP_OK;
        }
        head_ = layout.head % kCapacity;
        count_ = layout.count > kCapacity ? kCapacity : layout.count;
        std::memcpy(events_, layout.events, sizeof(events_));
        return ESP_OK;
    }

    if (stored_size == sizeof(LegacyPersistedLayoutV0)) {
        // Migra o layout pré-#124: converte cada evento (horario_provisorio =
        // false) e regrava no layout novo. Sem isso, eventos de preparo ainda
        // não publicados seriam descartados silenciosamente (FW-19).
        LegacyPersistedLayoutV0 legacy{};
        err = nvs_.load_blob(kKey, &legacy, sizeof(legacy));
        if (err != ESP_OK) {
            return err;
        }
        head_ = legacy.head % kCapacity;
        count_ = legacy.count > kCapacity ? kCapacity : legacy.count;
        for (size_t i = 0; i < kCapacity; ++i) {
            events_[i] = Event{};
            events_[i].timestamp_inicio = legacy.events[i].timestamp_inicio;
            events_[i].timestamp_fim = legacy.events[i].timestamp_fim;
            events_[i].origem = legacy.events[i].origem;
            events_[i].horario_provisorio = false;
        }
        ESP_LOGW("EventQueueStore", "migrando fila legada (%u eventos)",
                 static_cast<unsigned>(count_));
        return persist();
    }

    // Tamanho totalmente desconhecido: descarta e segue com fila vazia.
    ESP_LOGW("EventQueueStore", "blob de %u bytes incompativel, fila zerada",
             static_cast<unsigned>(stored_size));
    head_ = 0;
    count_ = 0;
    return ESP_OK;
}

esp_err_t EventQueueStore::persist() {
    PersistedLayout layout{};
    layout.magic = kEventQueueMagic;
    layout.schema_version = kEventQueueSchemaVersion;
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
    layout.magic = kEventQueueMagic;
    layout.schema_version = kEventQueueSchemaVersion;
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

esp_err_t EventQueueStore::front(Event* out_event) const {
    if (empty()) {
        return ESP_ERR_INVALID_STATE;
    }
    if (out_event != nullptr) {
        *out_event = events_[head_];
    }
    return ESP_OK;
}

esp_err_t EventQueueStore::at(size_t index, Event* out_event) const {
    if (index >= count_) {
        return ESP_ERR_INVALID_STATE;
    }
    if (out_event != nullptr) {
        *out_event = events_[(head_ + index) % kCapacity];
    }
    return ESP_OK;
}

size_t EventQueueStore::remove_confirmed(const uint32_t* confirmed_inicios, size_t count) {
    if (confirmed_inicios == nullptr || count == 0) {
        return 0;
    }

    // Compacta a fila mantendo apenas os nao-confirmados, em ordem FIFO.
    Event kept[kCapacity];
    size_t kept_count = 0;
    size_t removed = 0;
    for (size_t i = 0; i < count_; ++i) {
        const Event& event = events_[(head_ + i) % kCapacity];
        bool confirmed = false;
        for (size_t j = 0; j < count; ++j) {
            if (confirmed_inicios[j] == event.timestamp_inicio) {
                confirmed = true;
                break;
            }
        }
        if (confirmed) {
            ++removed;
        } else {
            kept[kept_count++] = event;
        }
    }

    if (removed == 0) {
        return 0;
    }

    PersistedLayout layout{};
    layout.magic = kEventQueueMagic;
    layout.schema_version = kEventQueueSchemaVersion;
    layout.head = 0;
    layout.count = static_cast<uint32_t>(kept_count);
    for (size_t i = 0; i < kept_count; ++i) {
        layout.events[i] = kept[i];
    }

    // Persiste PRIMEIRO; RAM so muda apos sucesso (mesma disciplina de push/pop).
    esp_err_t err = nvs_.save_blob(kKey, &layout, sizeof(layout));
    if (err != ESP_OK) {
        return 0;
    }

    std::memcpy(events_, layout.events, sizeof(events_));
    head_ = 0;
    count_ = kept_count;
    return removed;
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
    layout.magic = kEventQueueMagic;
    layout.schema_version = kEventQueueSchemaVersion;
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
