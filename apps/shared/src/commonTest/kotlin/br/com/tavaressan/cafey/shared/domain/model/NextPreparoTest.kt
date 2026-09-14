package br.com.tavaressan.cafey.shared.domain.model

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Issue #176 — cálculo puro do "próximo preparo" a partir dos agendamentos ativos do dispositivo. */
class NextPreparoTest {

    private fun agendamento(hora: String, diasSemana: Short, ativo: Boolean = true) = AgendamentoResponse(
        id = "a-$hora-$diasSemana",
        dispositivoId = "d1",
        hora = hora,
        diasSemana = diasSemana,
        ativo = ativo,
        criadoEm = "2026-01-01T00:00:00Z",
        atualizadoEm = "2026-01-01T00:00:00Z",
    )

    @Test
    fun semAgendamentos_retornaNull() {
        assertNull(calcularProximoPreparo(emptyList(), LocalDateTime(2026, 9, 14, 8, 0)))
    }

    @Test
    fun semAgendamentoAtivo_retornaNull() {
        val agendamentos = listOf(agendamento(hora = "07:00", diasSemana = 127, ativo = false))
        assertNull(calcularProximoPreparo(agendamentos, LocalDateTime(2026, 9, 14, 8, 0)))
    }

    @Test
    fun horarioAindaNaoPassouHoje_escolheHoje() {
        // 2026-09-14 é segunda-feira.
        val agendamentos = listOf(agendamento(hora = "18:00", diasSemana = 127))
        val resultado = calcularProximoPreparo(agendamentos, LocalDateTime(2026, 9, 14, 8, 0))

        assertEquals(0, resultado?.diasAteOProximo)
        assertEquals("18:00", resultado?.agendamento?.hora)
    }

    @Test
    fun horarioJaPassouHoje_escolheAmanha() {
        val agendamentos = listOf(agendamento(hora = "07:00", diasSemana = 127))
        val resultado = calcularProximoPreparo(agendamentos, LocalDateTime(2026, 9, 14, 8, 0))

        assertEquals(1, resultado?.diasAteOProximo)
    }

    @Test
    fun viradaDeSemana_sabadoNoiteParaDomingoDeManha() {
        // 2026-09-19 é sábado. Agendamento só aos domingos, de manhã.
        val agendamentos = listOf(agendamento(hora = "08:00", diasSemana = 1)) // bit 0 = domingo
        val resultado = calcularProximoPreparo(agendamentos, LocalDateTime(2026, 9, 19, 22, 0))

        assertEquals(1, resultado?.diasAteOProximo)
        assertEquals("08:00", resultado?.agendamento?.hora)
    }

    @Test
    fun multiplosAgendamentos_escolheOMaisProximo() {
        val agendamentos = listOf(
            agendamento(hora = "20:00", diasSemana = 127),
            agendamento(hora = "07:00", diasSemana = 127),
        )
        val resultado = calcularProximoPreparo(agendamentos, LocalDateTime(2026, 9, 14, 8, 0))

        assertEquals(0, resultado?.diasAteOProximo)
        assertEquals("20:00", resultado?.agendamento?.hora)
    }
}
