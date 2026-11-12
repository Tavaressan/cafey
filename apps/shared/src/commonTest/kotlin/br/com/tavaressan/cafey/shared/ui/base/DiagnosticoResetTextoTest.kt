package br.com.tavaressan.cafey.shared.ui.base

import kotlin.test.Test
import kotlin.test.assertEquals

class DiagnosticoResetTextoTest {
    @Test
    fun mapeiaMotivosConhecidosSemJargao() {
        assertEquals("normal", textoUltimoReinicio("RESET_NORMAL"))
        assertEquals("travamento (watchdog)", textoUltimoReinicio("RESET_WATCHDOG"))
        assertEquals("falha (pânico)", textoUltimoReinicio("RESET_PANIC"))
        assertEquals("queda de energia (brownout)", textoUltimoReinicio("RESET_BROWNOUT"))
    }

    @Test
    fun motivoDesconhecidoApareceComoVeio() {
        assertEquals("RESET_MISTERIOSO", textoUltimoReinicio("RESET_MISTERIOSO"))
    }
}
