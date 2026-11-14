package br.com.tavaressan.cafey.shared.ui.base

import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.TimeZone

@OptIn(ExperimentalTime::class)
class DiagnosticoTextosTest {
    private val utc = TimeZone.UTC
    private val agora = Instant.parse("2026-11-13T15:00:00Z").toEpochMilliseconds()

    @Test
    fun comunicacaoRecenteViraTempoRelativo() {
        assertEquals("agora mesmo", textoUltimaComunicacao("2026-11-13T14:59:40Z", agora, utc))
        assertEquals("agora mesmo", textoUltimaComunicacao("2026-11-13T15:00:30Z", agora, utc))
        assertEquals("há 2 min", textoUltimaComunicacao("2026-11-13T14:58:00Z", agora, utc))
        assertEquals("há 59 min", textoUltimaComunicacao("2026-11-13T14:01:00Z", agora, utc))
    }

    @Test
    fun comunicacaoAntigaViraDataLegivel() {
        assertEquals("hoje às 09:05", textoUltimaComunicacao("2026-11-13T09:05:00Z", agora, utc))
        assertEquals("ontem às 21:30", textoUltimaComunicacao("2026-11-12T21:30:00Z", agora, utc))
        assertEquals("10/11 às 08:00", textoUltimaComunicacao("2026-11-10T08:00:00Z", agora, utc))
    }

    @Test
    fun valorAusenteOuInvalidoViraIndisponivel() {
        assertEquals("Indisponível", textoUltimaComunicacao(null, agora, utc))
        assertEquals("Indisponível", textoUltimaComunicacao("ontem", agora, utc))
    }

    @Test
    fun mapeiaEstadoDoRele() {
        assertEquals("Ligado", textoEstadoRele("LIGADO"))
        assertEquals("Desligado", textoEstadoRele("DESLIGADO"))
        assertEquals("Indisponível", textoEstadoRele(null))
    }

    @Test
    fun formataTemperaturaComVirgulaEUmaCasa() {
        assertEquals("24,5 °C", textoTemperatura(24.5))
        assertEquals("24,0 °C", textoTemperatura(24.0))
        assertEquals("-5,3 °C", textoTemperatura(-5.3))
        assertEquals("0,0 °C", textoTemperatura(-0.04))
        assertEquals("Indisponível", textoTemperatura(null))
    }

    @Test
    fun formataUmidadeEmPorcento() {
        assertEquals("55 %", textoUmidade(55))
        assertEquals("Indisponível", textoUmidade(null))
    }

    @Test
    fun classificaONivelDeTemperatura() {
        assertEquals(NivelTemperatura.Normal, nivelTemperatura(null))
        assertEquals(NivelTemperatura.Normal, nivelTemperatura(44.9))
        assertEquals(NivelTemperatura.Atencao, nivelTemperatura(45.0))
        assertEquals(NivelTemperatura.Atencao, nivelTemperatura(54.9))
        assertEquals(NivelTemperatura.Alta, nivelTemperatura(55.0))
    }

    @Test
    fun alarmeSoValeComLeituraRecente() {
        val recente = "2026-11-13T14:58:00Z" // 2 min antes de "agora"
        val antiga = "2026-11-13T14:30:00Z"
        assertEquals(null, textoAlarmeTemperatura(60.0, antiga, agora))
        assertEquals(null, textoAlarmeTemperatura(60.0, null, agora))
        assertEquals(null, textoAlarmeTemperatura(30.0, recente, agora))
        assertEquals(null, textoAlarmeTemperatura(null, recente, agora))
    }

    @Test
    fun alarmeTemTextoPorNivel() {
        val recente = "2026-11-13T14:58:00Z"
        val atencao = textoAlarmeTemperatura(47.5, recente, agora)
        assertEquals(true, atencao?.contains("47,5 °C") == true && atencao.contains("quente"))
        val alta = textoAlarmeTemperatura(58.0, recente, agora)
        assertEquals(true, alta?.contains("58,0 °C") == true && alta.contains("Desligue"))
    }
}
