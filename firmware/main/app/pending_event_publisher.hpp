#pragma once

#include <cstddef>
#include <string>

#include "core/mqtt_client.hpp"
#include "storage/event.hpp"
#include "storage/event_queue_store.hpp"

namespace cafey::app {

/**
 * @brief Ponte entre a fila de eventos persistida (FW-06) e o cliente MQTT
 * (FW-10): enfileira eventos gerados offline e os drena ao reconectar (FW-16,
 * spec §5.5 "os eventos gerados no periodo ficam enfileirados e sao enviados
 * quando a conectividade retorna").
 */
class PendingEventPublisher {
public:
    PendingEventPublisher(cafey::storage::EventQueueStore& store,
                          cafey::core::IMqttClient& client,
                          std::string eventos_topic)
        : store_(store), client_(client), topic_(std::move(eventos_topic)) {}

    /**
     * @brief Enfileira um evento na fila persistida. Sempre persiste primeiro
     * (autonomia offline): o envio fica a cargo de drain().
     */
    esp_err_t enqueue(const cafey::storage::Event& event) {
        return store_.push(event);
    }

    /**
     * @brief Publica os eventos pendentes na ordem FIFO enquanto o cliente
     * estiver conectado. Cada evento so e removido da fila apos publish() aceito;
     * na primeira falha o loop para, preservando o restante para a proxima
     * tentativa.
     * @return numero de eventos efetivamente drenados.
     */
    size_t drain() {
        size_t sent = 0;
        cafey::storage::Event event{};
        while (client_.is_connected() && store_.front(&event) == ESP_OK) {
            if (!client_.publish(topic_, serialize(event), cafey::core::kMqttQos1,
                                 /*retain=*/false)) {
                break;
            }
            store_.pop(nullptr);
            ++sent;
        }
        return sent;
    }

    /** @brief Serializa o evento no formato de `dispositivos/{id}/eventos` (spec §6.2). */
    static std::string serialize(const cafey::storage::Event& event) {
        std::string json = "{\"inicio\":" + std::to_string(event.timestamp_inicio) +
                           ",\"fim\":" + std::to_string(event.timestamp_fim) +
                           ",\"origem\":\"" + origin_name(event.origem) + "\"";
        // FW-15: sinaliza ao backend que o carimbo e provisorio (preparo antes
        // do sync NTP). Ausente quando o horario ja e confiavel.
        if (event.horario_provisorio) {
            json += ",\"relogioProvisorio\":true";
        }
        json += "}";
        return json;
    }

private:
    static const char* origin_name(cafey::storage::EventOrigin origin) {
        switch (origin) {
            case cafey::storage::EventOrigin::APP: return "APP";
            case cafey::storage::EventOrigin::AGENDAMENTO: return "AGENDAMENTO";
            case cafey::storage::EventOrigin::BOTAO: return "BOTAO";
        }
        return "APP";
    }

    cafey::storage::EventQueueStore& store_;
    cafey::core::IMqttClient& client_;
    std::string topic_;
};

} // namespace cafey::app
