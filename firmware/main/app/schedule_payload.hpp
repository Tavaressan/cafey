#pragma once

#include <cstddef>
#include <cstdint>
#include <string>

#include "storage/schedule.hpp"

namespace cafey::app {

/** @brief Metadados da lista de agendamentos (spec-backend §6.2). */
struct SchedulePayloadMeta {
    uint32_t version = 0;     // `versao` monotonica
    uint32_t duracao_s = 0;   // `duracaoS` do disparo agendado (offline)
    bool has_duracao = false;
};

/**
 * @brief Faz o parse do payload de agendamentos compartilhado entre o downlink
 * MQTT retido (`dispositivos/{id}/agendamentos`) e a caracteristica BLE de
 * agendamentos (FW-17, spec-backend §6.2 / §6.3).
 *
 * Substitui a lista inteira (idempotente). Preenche ate `max` itens em `out` e
 * o total em `out_count`; `meta` recebe versao/duracao.
 * @return false apenas se `out`/`out_count` forem nulos.
 */
bool parse_schedule_payload(const std::string& json, cafey::storage::Schedule* out,
                            size_t max, size_t* out_count, SchedulePayloadMeta* meta);

} // namespace cafey::app
