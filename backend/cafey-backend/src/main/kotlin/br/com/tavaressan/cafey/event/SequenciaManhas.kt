package br.com.tavaressan.cafey.event

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Issue #177 — cálculo da "sequência de manhãs" exibida na Home. Não há especificação escrita
 * além do protótipo visual (`docs/docs_interface/prototype/home.html`, bloco "Sequência de
 * manhãs"), então a regra abaixo é uma decisão de produto explícita, não uma dedução do backend
 * existente:
 *
 * - Conta dias **consecutivos** (sem nenhum dia faltando no meio) em que houve pelo menos um
 *   evento de preparo `PREPARO`/`CONCLUIDO` com horário local antes de [CORTE_MANHA] (meio-dia).
 * - A sequência termina em "hoje" se hoje já teve um preparo matinal; senão, termina em "ontem" —
 *   o dia de hoje ainda não acabou, então a ausência de preparo matinal *ainda* não quebra a
 *   sequência (só quebra amanhã, se hoje passar em branco).
 * - Assim que aparece um dia sem preparo matinal andando para trás no tempo, a contagem para.
 */
object SequenciaManhas {
    val CORTE_MANHA: LocalTime = LocalTime.NOON

    /**
     * @param timestampsConcluidos timestamps (UTC) de eventos PREPARO/CONCLUIDO do dispositivo.
     * @param timezone fuso horário do dispositivo, para converter timestamp em dia/hora local.
     * @param agora instante de referência ("agora"), injetável para teste.
     */
    fun calcular(timestampsConcluidos: List<Instant>, timezone: ZoneId, agora: Instant): Int {
        val diasComPreparoMatinal = timestampsConcluidos
            .map { it.atZone(timezone) }
            .filter { it.toLocalTime().isBefore(CORTE_MANHA) }
            .map { it.toLocalDate() }
            .toSet()

        val hoje = agora.atZone(timezone).toLocalDate()
        var dia = if (diasComPreparoMatinal.contains(hoje)) hoje else hoje.minusDays(1)

        var sequencia = 0
        while (diasComPreparoMatinal.contains(dia)) {
            sequencia++
            dia = dia.minusDays(1)
        }
        return sequencia
    }
}
