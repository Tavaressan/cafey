package br.com.tavaressan.cafey.shared.ui.home

import br.com.tavaressan.cafey.shared.domain.model.DeviceState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class BrewingProgressTest {

    private val inicio = "2026-11-09T12:00:00Z"
    private val inicioMs = Instant.parse(inicio).toEpochMilliseconds()
    private val duracaoS = 300

    @Test
    fun `0 por cento no inicio do preparo`() {
        val progresso = calcularProgressoPreparo(
            deviceState = DeviceState.Brewing,
            preparoIniciadoEm = inicio,
            duracaoPreparoS = duracaoS,
            nowEpochMs = inicioMs,
        )

        val determinado = assertIs<BrewingProgress.Determinate>(progresso)
        assertEquals(0f, determinado.fraction)
    }

    @Test
    fun `meio do preparo`() {
        val progresso = calcularProgressoPreparo(
            deviceState = DeviceState.Brewing,
            preparoIniciadoEm = inicio,
            duracaoPreparoS = duracaoS,
            nowEpochMs = inicioMs + 150_000L,
        )

        val determinado = assertIs<BrewingProgress.Determinate>(progresso)
        assertEquals(0.5f, determinado.fraction)
    }

    @Test
    fun `100 por cento ao atingir a duracao`() {
        val progresso = calcularProgressoPreparo(
            deviceState = DeviceState.Brewing,
            preparoIniciadoEm = inicio,
            duracaoPreparoS = duracaoS,
            nowEpochMs = inicioMs + 300_000L,
        )

        val determinado = assertIs<BrewingProgress.Determinate>(progresso)
        assertEquals(1f, determinado.fraction)
    }

    @Test
    fun `100 por cento permanece limitado quando o tempo ultrapassa a duracao`() {
        val progresso = calcularProgressoPreparo(
            deviceState = DeviceState.Brewing,
            preparoIniciadoEm = inicio,
            duracaoPreparoS = duracaoS,
            nowEpochMs = inicioMs + 600_000L,
        )

        val determinado = assertIs<BrewingProgress.Determinate>(progresso)
        assertEquals(1f, determinado.fraction)
    }

    @Test
    fun `cancelado recolhe o anel ao sair de preparando`() {
        val progresso = calcularProgressoPreparo(
            deviceState = DeviceState.Idle,
            preparoIniciadoEm = inicio,
            duracaoPreparoS = duracaoS,
            nowEpochMs = inicioMs + 150_000L,
        )

        assertIs<BrewingProgress.Inactive>(progresso)
    }

    @Test
    fun `sem desde em preparando fica indeterminado`() {
        val progresso = calcularProgressoPreparo(
            deviceState = DeviceState.Brewing,
            preparoIniciadoEm = null,
            duracaoPreparoS = duracaoS,
            nowEpochMs = inicioMs + 150_000L,
        )

        assertIs<BrewingProgress.Indeterminate>(progresso)
    }

    @Test
    fun `desde invalido em preparando fica indeterminado`() {
        val progresso = calcularProgressoPreparo(
            deviceState = DeviceState.Brewing,
            preparoIniciadoEm = "nao-e-uma-data",
            duracaoPreparoS = duracaoS,
            nowEpochMs = inicioMs + 150_000L,
        )

        assertIs<BrewingProgress.Indeterminate>(progresso)
    }

    @Test
    fun `estado pronto recebido da cafeteira nao mostra anel de preparo`() {
        val progresso = calcularProgressoPreparo(
            deviceState = DeviceState.Ready,
            preparoIniciadoEm = inicio,
            duracaoPreparoS = duracaoS,
            nowEpochMs = inicioMs + 300_000L,
        )

        assertTrue(progresso is BrewingProgress.Inactive)
    }
}
