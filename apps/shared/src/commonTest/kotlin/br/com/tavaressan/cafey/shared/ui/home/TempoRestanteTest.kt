package br.com.tavaressan.cafey.shared.ui.home

import br.com.tavaressan.cafey.shared.domain.model.DeviceState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

@OptIn(ExperimentalTime::class)
class TempoRestanteTest {
    private val inicio = "2026-11-14T07:00:00Z"
    private val inicioMs = Instant.parse(inicio).toEpochMilliseconds()

    @Test
    fun formataMinutosESegundos() {
        assertEquals("4:32", textoTempoRestante(272))
        assertEquals("5:00", textoTempoRestante(300))
        assertEquals("1:00", textoTempoRestante(60))
        assertEquals("10:05", textoTempoRestante(605))
    }

    @Test
    fun formataHoras() {
        assertEquals("1:02:03", textoTempoRestante(3723))
    }

    @Test
    fun abaixoDeUmMinutoViraMenosDeUmMinuto() {
        assertEquals("menos de 1 min", textoTempoRestante(59))
        assertEquals("menos de 1 min", textoTempoRestante(1))
    }

    @Test
    fun zeroOuNegativoViraAgora() {
        assertEquals("agora", textoTempoRestante(0))
        assertEquals("agora", textoTempoRestante(-5))
    }

    @Test
    fun estimadoGanhaTilAcimaDeUmMinuto() {
        assertEquals("~4:32", textoTempoRestante(272, estimado = true))
        assertEquals("menos de 1 min", textoTempoRestante(30, estimado = true))
    }

    @Test
    fun horarioPrevistoUsaOFusoInformado() {
        val fim = Instant.parse("2026-11-14T10:42:00Z").toEpochMilliseconds()
        assertEquals("pronto às 10:42", textoHorarioPrevisto(fim, TimeZone.UTC))
        assertEquals("pronto às 07:42", textoHorarioPrevisto(fim, TimeZone.of("America/Sao_Paulo")))
    }

    @Test
    fun descricaoAcessivelMudaACada30Segundos() {
        assertEquals("faltam 5 minutos", textoDescricao(272)) // arredonda para cima
        assertEquals("faltam 4 minutos e 30 segundos", textoDescricao(256))
        assertEquals("faltam 4 minutos e 30 segundos", textoDescricao(270))
        assertEquals("faltam 4 minutos", textoDescricao(240))
        assertEquals("faltam 1 minuto", textoDescricao(60))
        assertEquals("falta menos de um minuto", textoDescricao(45))
    }

    private fun textoDescricao(s: Long) = descricaoRestanteAcessivel(s)

    @Test
    fun progressoCalculaRestanteArredondandoParaCima() {
        val p = assertIs<BrewingProgress.Determinate>(
            calcularProgressoPreparo(DeviceState.Brewing, inicio, 300, inicioMs + 27_800L),
        )
        assertEquals(273L, p.restanteS) // faltam 272,2 s
        assertEquals(inicioMs + 300_000L, p.terminaEmMs)
    }

    @Test
    fun restanteNuncaNegativoComRelogioAdiantado() {
        val p = assertIs<BrewingProgress.Determinate>(
            calcularProgressoPreparo(DeviceState.Brewing, inicio, 300, inicioMs + 999_000L),
        )
        assertEquals(0L, p.restanteS)
        assertEquals(1f, p.fraction)
    }

    @Test
    fun relogioDoCelularAtrasadoNaoPassaDaDuracao() {
        val p = assertIs<BrewingProgress.Determinate>(
            calcularProgressoPreparo(DeviceState.Brewing, inicio, 300, inicioMs - 60_000L),
        )
        assertEquals(300L, p.restanteS)
        assertEquals(0f, p.fraction)
    }

    @Test
    fun semInicioOuDuracaoNaoHaTempo() {
        assertIs<BrewingProgress.Indeterminate>(calcularProgressoPreparo(DeviceState.Brewing, null, 300, 0))
        assertIs<BrewingProgress.Indeterminate>(calcularProgressoPreparo(DeviceState.Brewing, inicio, 0, 0))
    }
}
