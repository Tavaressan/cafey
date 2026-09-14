package br.com.tavaressan.cafey.shared.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** UC-15 — gráfico de distribuição por origem sobre `EstatisticasConsumoResponse` (APP-10). */
class EstatisticasChartTest {
    private fun estatisticas(porOrigem: Map<String, Long>) = EstatisticasConsumoResponse(
        totalPreparosConcluidos = porOrigem.values.sum(),
        porOrigem = porOrigem,
        tempoTotalPreparoSegundos = 0,
    )

    @Test
    fun barrasPorOrigem_ordenaDaMaiorParaAMenor() {
        val barras = estatisticas(mapOf("BOTAO" to 3, "APP" to 10, "AGENDAMENTO" to 5)).barrasPorOrigem()

        assertEquals(listOf("APP", "AGENDAMENTO", "BOTAO"), barras.map { it.origem })
    }

    @Test
    fun barrasPorOrigem_calculaFracaoRelativaAoMaiorValor() {
        val barras = estatisticas(mapOf("APP" to 10, "AGENDAMENTO" to 5)).barrasPorOrigem()

        assertEquals(1.0f, barras.first { it.origem == "APP" }.fracao)
        assertEquals(0.5f, barras.first { it.origem == "AGENDAMENTO" }.fracao)
    }

    @Test
    fun barrasPorOrigem_semEventosRetornaListaVazia() {
        val barras = estatisticas(emptyMap()).barrasPorOrigem()

        assertTrue(barras.isEmpty())
    }
}
