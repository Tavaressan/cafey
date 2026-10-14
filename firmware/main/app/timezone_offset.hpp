#pragma once

#include <cstdint>
#include <string>

namespace cafey::app {

/**
 * @brief Converte o `timezone` IANA do payload de agendamentos (spec-backend
 * §7.2) no deslocamento fixo em relacao a UTC, em segundos (FW-23).
 *
 * Cobre os fusos do Brasil, que nao tem horario de verao desde o Decreto
 * 9.772/2019; por isso basta um offset fixo, sem regra POSIX nem tzset. Se o
 * horario de verao voltar, esta tabela vira regra POSIX; o IANA gravado em NVS
 * continua valido.
 *
 * @return false para fuso desconhecido; `*out` fica intacto.
 */
bool utc_offset_s(const std::string& iana, int32_t* out);

} // namespace cafey::app
