#pragma once

#include <cstddef>
#include <cstdint>
#include <cstring>

namespace cafey::storage {

/**
 * @brief Origin of a brewing event (spec §7.2 table `eventos_preparo`).
 */
enum class EventOrigin : uint8_t {
    APP = 0,
    AGENDAMENTO = 1,
    BOTAO = 2,
};

/**
 * @brief Result of a brewing event (spec §7.2 table `eventos_preparo`).
 */
enum class EventResult : uint8_t {
    CONCLUIDO = 0,
    CANCELADO = 1,
    ERRO = 2,
};

/**
 * @brief A single pending brewing event, queued locally until connectivity
 * allows publishing it to the cloud (spec §5.4, §5.5).
 *
 * Layout de 40 bytes persistido na EventQueueStore com schema v2 (FW-22).
 */
struct Event {
    static constexpr size_t kMaxEventoIdLen = 24;

    uint32_t timestamp_inicio = 0; // epoch seconds
    uint32_t timestamp_fim = 0;    // epoch seconds
    uint32_t duracao_s = 0;        // duracao da extracao em segundos
    EventOrigin origem = EventOrigin::APP;
    EventResult resultado = EventResult::CONCLUIDO;
    bool horario_provisorio = false;
    uint8_t reserved = 0;
    char evento_id[kMaxEventoIdLen] = {};
};

static_assert(sizeof(Event) == 40, "Event struct must be exactly 40 bytes");

/**
 * @brief Monta um evento de preparo completo (FW-22).
 */
inline Event make_brew_event(const char* evento_id, EventOrigin origem,
                             EventResult resultado, uint32_t duracao_s,
                             uint32_t timestamp_inicio, uint32_t timestamp_fim,
                             bool ntp_synced) {
    Event event{};
    if (evento_id != nullptr) {
        std::strncpy(event.evento_id, evento_id, sizeof(event.evento_id) - 1);
        event.evento_id[sizeof(event.evento_id) - 1] = '\0';
    }
    event.timestamp_inicio = timestamp_inicio;
    event.timestamp_fim = timestamp_fim;
    event.duracao_s = duracao_s;
    event.origem = origem;
    event.resultado = resultado;
    event.horario_provisorio = !ntp_synced;
    return event;
}

/**
 * @brief Sobrecarga de compatibilidade para testes legados (FW-15).
 */
inline Event make_brew_event(EventOrigin origem, uint32_t timestamp_inicio,
                             uint32_t timestamp_fim, bool ntp_synced) {
    const uint32_t duracao = (timestamp_fim >= timestamp_inicio)
                                 ? (timestamp_fim - timestamp_inicio)
                                 : 0;
    return make_brew_event("", origem, EventResult::CONCLUIDO, duracao,
                           timestamp_inicio, timestamp_fim, ntp_synced);
}

} // namespace cafey::storage
