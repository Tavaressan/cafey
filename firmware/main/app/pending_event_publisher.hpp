#pragma once

#include <cstddef>
#include <cstdint>
#include <ctime>
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

    /**
     * @brief Formata segundos Unix em ISO-8601 UTC (ex: "2026-09-01T13:00:04Z").
     */
    static std::string to_iso8601_utc(uint32_t epoch_s) {
        time_t raw_time = static_cast<time_t>(epoch_s);
        struct tm tm_buf{};
#if defined(_WIN32)
        gmtime_s(&tm_buf, &raw_time);
#else
        gmtime_r(&raw_time, &tm_buf);
#endif
        char buf[32];
        size_t len = std::strftime(buf, sizeof(buf), "%Y-%m-%dT%H:%M:%SZ", &tm_buf);
        return std::string(buf, len);
    }

    /**
     * @brief Serializa o evento no formato de `EventoPayload` (spec-backend §6.2 / FW-22).
     */
    static std::string serialize(const cafey::storage::Event& event) {
        const uint32_t ts = event.timestamp_inicio != 0 ? event.timestamp_inicio : event.timestamp_fim;
        std::string json = "{\"eventoId\":\"" + std::string(event.evento_id) +
                           "\",\"tipo\":\"PREPARO\"" +
                           ",\"resultado\":\"" + result_name(event.resultado) + "\"" +
                           ",\"origem\":\"" + origin_name(event.origem) + "\"" +
                           ",\"duracaoS\":" + std::to_string(event.duracao_s) +
                           ",\"timestamp\":\"" + to_iso8601_utc(ts) + "\"";
        // FW-15 / FW-22: carimbo provisorio quando o preparo ocorreu sem sincronizacao NTP.
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

    static const char* result_name(cafey::storage::EventResult result) {
        switch (result) {
            case cafey::storage::EventResult::CONCLUIDO: return "CONCLUIDO";
            case cafey::storage::EventResult::CANCELADO: return "CANCELADO";
            case cafey::storage::EventResult::ERRO: return "ERRO";
        }
        return "CONCLUIDO";
    }

    cafey::storage::EventQueueStore& store_;
    cafey::core::IMqttClient& client_;
    std::string topic_;
};

} // namespace cafey::app
