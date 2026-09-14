package br.com.tavaressan.cafey.shared.domain.model

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.isoDayNumber

/** Rótulos por extenso dos 7 dias, mesma ordem/índice de [DIAS_SEMANA_LABELS] (0 = domingo). */
val DIAS_SEMANA_NOMES = listOf(
    "domingo", "segunda-feira", "terça-feira", "quarta-feira", "quinta-feira", "sexta-feira", "sábado",
)

/**
 * Resultado do cálculo de próximo preparo (issue #176): qual [agendamento] dispara em seguida e
 * quantos dias faltam a partir de `agora` (0 = hoje, 1 = amanhã, 2–6 = daqui a N dias).
 */
data class ProximoPreparo(
    val agendamento: AgendamentoResponse,
    val diasAteOProximo: Int,
)

/**
 * Calcula o próximo preparo a partir dos agendamentos do dispositivo (`ScheduleApi.listar`),
 * usando apenas dado real — sem inventar horário. Considera só `ativo == true`; para cada um,
 * percorre os próximos 7 dias (hoje incluído) procurando o primeiro dia da semana marcado em
 * `diasSemana`, e entre os candidatos de todos os agendamentos fica com o mais próximo no tempo.
 * Cobre virada de semana porque os dias são módulo 7 a partir do dia da semana de `agora`.
 *
 * Retorna `null` quando não há nenhum agendamento ativo (estado vazio — ver `HomeScreen`).
 */
fun calcularProximoPreparo(agendamentos: List<AgendamentoResponse>, agora: LocalDateTime): ProximoPreparo? {
    val ativos = agendamentos.filter { it.ativo }
    if (ativos.isEmpty()) return null

    val minutoAgora = agora.hour * 60 + agora.minute
    val diaSemanaAgora = agora.dayOfWeek.isoDayNumber % 7 // domingo=0 ... sábado=6

    var melhorAgendamento: AgendamentoResponse? = null
    var melhorOffset = -1
    var melhorMinutosAte = Int.MAX_VALUE

    for (agendamento in ativos) {
        val partesHora = agendamento.hora.split(":")
        val minutoAgendamento = partesHora[0].toInt() * 60 + partesHora[1].toInt()

        for (offset in 0..6) {
            val dia = (diaSemanaAgora + offset) % 7
            if (!agendamento.diasSemana.diaAtivo(dia)) continue
            if (offset == 0 && minutoAgendamento <= minutoAgora) continue

            val minutosAte = offset * 1440 + (minutoAgendamento - minutoAgora)
            if (minutosAte < melhorMinutosAte) {
                melhorMinutosAte = minutosAte
                melhorAgendamento = agendamento
                melhorOffset = offset
            }
            break
        }
    }

    return melhorAgendamento?.let { ProximoPreparo(it, melhorOffset) }
}
