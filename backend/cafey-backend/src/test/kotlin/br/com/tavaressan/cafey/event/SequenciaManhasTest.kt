package br.com.tavaressan.cafey.event

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Issue #177 — regra de "sequência de manhãs": dias consecutivos (terminando hoje ou ontem, para
 * não quebrar a sequência de quem ainda vai preparar hoje) com pelo menos um evento PREPARO
 * CONCLUIDO cujo horário local seja antes do meio-dia ([SequenciaManhas.CORTE_MANHA]). Decisão de
 * produto documentada aqui na ausência de uma definição escrita além do protótipo visual.
 */
class SequenciaManhasTest {

    private val zone = ZoneOffset.UTC
    private fun instanteEm(dia: String, hora: String) = Instant.parse("${dia}T${hora}:00Z")

    @Test
    fun `sem eventos retorna zero`() {
        assertEquals(0, SequenciaManhas.calcular(emptyList(), zone, instanteEm("2026-09-14", "10:00")))
    }

    @Test
    fun `sequencia ativa conta dias consecutivos terminando hoje`() {
        val timestamps = listOf(
            instanteEm("2026-09-12", "07:00"),
            instanteEm("2026-09-13", "06:30"),
            instanteEm("2026-09-14", "08:00"),
        )
        val agora = instanteEm("2026-09-14", "09:00")

        assertEquals(3, SequenciaManhas.calcular(timestamps, zone, agora))
    }

    @Test
    fun `sequencia continua valida se hoje ainda nao teve preparo matinal`() {
        val timestamps = listOf(
            instanteEm("2026-09-12", "07:00"),
            instanteEm("2026-09-13", "06:30"),
        )
        val agora = instanteEm("2026-09-14", "09:00") // hoje ainda sem preparo, mas o dia não acabou

        assertEquals(2, SequenciaManhas.calcular(timestamps, zone, agora))
    }

    @Test
    fun `sequencia quebrada por um dia sem preparo matinal`() {
        val timestamps = listOf(
            instanteEm("2026-09-10", "07:00"),
            // 09-11 sem preparo matinal — quebra a sequência
            instanteEm("2026-09-12", "07:00"),
            instanteEm("2026-09-13", "07:00"),
        )
        val agora = instanteEm("2026-09-14", "09:00")

        assertEquals(2, SequenciaManhas.calcular(timestamps, zone, agora))
    }

    @Test
    fun `preparo depois do meio-dia nao conta como manha`() {
        val timestamps = listOf(instanteEm("2026-09-14", "14:00"))
        val agora = instanteEm("2026-09-14", "15:00")

        assertEquals(0, SequenciaManhas.calcular(timestamps, zone, agora))
    }
}
