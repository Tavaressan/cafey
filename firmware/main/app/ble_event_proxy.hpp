#pragma once

#include <cstdint>
#include <string>
#include <vector>

#include "app/pending_event_publisher.hpp"
#include "core/ble_server.hpp"
#include "core/mini_json.hpp"
#include "storage/event.hpp"
#include "storage/event_queue_store.hpp"

namespace cafey::app {

/**
 * @brief Proxy BLE da fila de eventos pendentes (FW-18, spec §6.5).
 *
 * Expoe duas caracteristicas:
 *  - pendentes (read/notify): a fila persistida serializada como array JSON,
 *    cada item no mesmo formato de `dispositivos/{id}/eventos`;
 *  - confirmacao (write): `{"confirmados":[<inicio>,...]}` — o app relata quais
 *    eventos ja entregou ao backend, por `timestamp_inicio` (a chave de
 *    deduplicacao do backend). Apenas esses saem da fila; o resto permanece.
 */
class BleEventProxy {
public:
    static constexpr const char* kPendingUuid = "6b3d0005-0b0b-4a3a-9c0e-cafe1a000000";
    static constexpr const char* kConfirmUuid = "6b3d0006-0b0b-4a3a-9c0e-cafe1a000000";

    BleEventProxy(cafey::core::IBleServer& server, cafey::storage::EventQueueStore& queue)
        : server_(server), queue_(queue) {}

    /** @brief Registra as caracteristicas e publica o estado atual da fila. */
    void begin() {
        server_.add_characteristic(kPendingUuid,
                                   cafey::core::kBleRead | cafey::core::kBleNotify, nullptr);
        server_.add_characteristic(
            kConfirmUuid, cafey::core::kBleWrite,
            [this](const std::string& value) { handle_confirm(value); });
        publish_pending();
    }

    /**
     * @brief Serializa a fila pendente e atualiza a caracteristica de leitura.
     * @return o JSON publicado.
     */
    std::string publish_pending() {
        std::string json = "[";
        cafey::storage::Event event{};
        for (size_t i = 0; i < queue_.size(); ++i) {
            if (queue_.at(i, &event) != ESP_OK) break;
            if (i > 0) json += ",";
            json += PendingEventPublisher::serialize(event);
        }
        json += "]";
        server_.update_value(kPendingUuid, json);
        return json;
    }

    /**
     * @brief Trata a escrita de confirmacao: remove da fila os eventos cujo
     * `timestamp_inicio` o app confirmou e republica a fila restante.
     * @return numero de eventos removidos.
     */
    size_t handle_confirm(const std::string& json) {
        const std::vector<unsigned long> raw =
            cafey::core::mini_json::uint_array(json, "confirmados");
        size_t removed = 0;
        if (!raw.empty()) {
            std::vector<uint32_t> inicios;
            inicios.reserve(raw.size());
            for (unsigned long value : raw) {
                inicios.push_back(static_cast<uint32_t>(value));
            }
            removed = queue_.remove_confirmed(inicios.data(), inicios.size());
        }
        publish_pending();
        return removed;
    }

private:
    cafey::core::IBleServer& server_;
    cafey::storage::EventQueueStore& queue_;
};

} // namespace cafey::app
