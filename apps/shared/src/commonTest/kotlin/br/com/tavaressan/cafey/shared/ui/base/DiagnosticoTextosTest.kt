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
}
