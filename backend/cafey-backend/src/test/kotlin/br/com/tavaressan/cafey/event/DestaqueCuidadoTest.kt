package br.com.tavaressan.cafey.event

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DestaqueCuidadoTest {

    private fun fracoes(enxague: Double, filtro: Double, descalcificacao: Double) = mapOf(
        TipoCuidado.ENXAGUE to enxague,
        TipoCuidado.FILTRO to filtro,
        TipoCuidado.DESCALCIFICACAO to descalcificacao
    )

    @Test
    fun `fracao e contador dividido pelo limiar sem teto`() {
        assertEquals(0.85, DestaqueCuidado.fracao(34, 40))
        assertEquals(1.5, DestaqueCuidado.fracao(60, 40))
    }

    @Test
    fun `fracao com limiar nao positivo e zero`() {
        assertEquals(0.0, DestaqueCuidado.fracao(10, 0))
        assertEquals(0.0, DestaqueCuidado.fracao(10, -5))
    }

    @Test
    fun `escolhe o cuidado com a maior fracao`() {
        assertEquals(TipoCuidado.ENXAGUE, DestaqueCuidado.escolher(fracoes(0.85, 0.70, 0.15)))
        assertEquals(TipoCuidado.FILTRO, DestaqueCuidado.escolher(fracoes(0.25, 0.97, 0.15)))
        assertEquals(TipoCuidado.DESCALCIFICACAO, DestaqueCuidado.escolher(fracoes(0.25, 0.50, 1.00)))
    }

    @Test
    fun `cuidado mais estourado vence mesmo quando ambos passaram do limiar`() {
        assertEquals(TipoCuidado.FILTRO, DestaqueCuidado.escolher(fracoes(1.2, 1.9, 0.0)))
    }

    @Test
    fun `empate segue a ordem enxague filtro descalcificacao`() {
        assertEquals(TipoCuidado.ENXAGUE, DestaqueCuidado.escolher(fracoes(0.5, 0.5, 0.5)))
        assertEquals(TipoCuidado.FILTRO, DestaqueCuidado.escolher(fracoes(0.1, 0.5, 0.5)))
        assertEquals(TipoCuidado.ENXAGUE, DestaqueCuidado.escolher(fracoes(0.0, 0.0, 0.0)))
    }
}
