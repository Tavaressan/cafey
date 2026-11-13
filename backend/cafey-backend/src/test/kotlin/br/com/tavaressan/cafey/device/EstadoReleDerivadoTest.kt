package br.com.tavaressan.cafey.device

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EstadoReleDerivadoTest {
    @Test
    fun `rele fica ligado enquanto o modulo prepara`() {
        listOf("PREPARANDO", "LIGADO", "BREWING", " preparando ").forEach {
            assertEquals("LIGADO", estadoReleDerivado(it), it)
        }
    }

    @Test
    fun `rele fica desligado nos demais estados`() {
        listOf("DESLIGADO", "PRONTO", "", "ERRO").forEach {
            assertEquals("DESLIGADO", estadoReleDerivado(it), it)
        }
    }
}
