package br.com.tavaressan.cafey.shared.network

import br.com.tavaressan.cafey.shared.AppContainer
import br.com.tavaressan.cafey.shared.auth.FakeTokenStorage
import br.com.tavaressan.cafey.shared.domain.model.AcaoComando
import br.com.tavaressan.cafey.shared.domain.model.LoginRequest
import br.com.tavaressan.cafey.shared.domain.model.TipoCuidado
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Testes de integração do Modo Demo (Issue #184).
 *
 * Garante que:
 * 1. Com a flag de demo ativa (`isDemoMode = true`), todas as APIs de negócio
 *    (Auth, Dispositivos, Comandos, Agendamentos, Eventos/Histórico e Cuidados)
 *    respondem com dados consistentes usando o engine mock, sem efetuar nenhuma conexão
 *    de rede real nem depender de backend em execução.
 * 2. Com a flag desligada (`isDemoMode = false`), o comportamento original permanece inalterado.
 */
class DemoModeIntegrationTest {

    @Test
    fun demoContainer_allEndpointsReturnValidResponses_withoutRealNetwork() = runTest {
        val tokenStorage = FakeTokenStorage()
        val container = AppContainer.demo(tokenStorage = tokenStorage)

        assertTrue(container.isDemoMode, "Container deve estar explicitamente marcado como demo mode")
        assertFalse(container.apiClient.isAuthenticated(), "Deve iniciar não autenticado")

        // 1. Autenticação Demo
        val authResult = container.authApi.login(LoginRequest("demo@cafey.com", "123456"))
        assertNotNull(authResult.accessToken)
        assertTrue(container.apiClient.isAuthenticated(), "Deve estar autenticado após login")

        // 2. Dispositivos (Home)
        val dispositivos = container.deviceApi.listar()
        assertEquals(1, dispositivos.size)
        val cp30 = dispositivos.first()
        assertEquals("cafey-demo-cp30", cp30.id)
        assertEquals("Caféy Britânia CP30", cp30.nome)
        assertEquals("PRONTO", cp30.estado)
        assertTrue(cp30.online)

        // 3. Comandos (Ligar cafeteira)
        val comandoResult = container.commandApi.ligar(cp30.id)
        assertEquals(AcaoComando.LIGAR, comandoResult.acao)
        assertNotNull(comandoResult.comandoId)

        // 4. Agendamentos
        val agendamentos = container.scheduleApi.listar(cp30.id)
        assertEquals(2, agendamentos.size)
        assertEquals("07:00", agendamentos[0].hora)
        assertTrue(agendamentos[0].ativo)

        // 5. Histórico e Estatísticas
        val pageEventos = container.eventApi.listarEventos(cp30.id)
        assertEquals(3, pageEventos.content.size)
        assertEquals(3L, pageEventos.totalElements)

        val stats = container.eventApi.obterEstatisticas(cp30.id)
        assertEquals(42L, stats.totalPreparosConcluidos)
        assertEquals(22L, stats.porOrigem["APP"])

        // 6. Cuidados
        val cuidados = container.eventApi.obterCuidados(cp30.id)
        assertEquals(TipoCuidado.ENXAGUE, cuidados.destaque)
        assertEquals(8, cuidados.enxague.contadorPreparos)
        assertEquals(10, cuidados.enxague.limiarPreparos)

        val baixaEnxague = container.eventApi.darBaixaEnxague(cp30.id)
        assertEquals(0, baixaEnxague.contadorPreparos)
    }

    @Test
    fun normalContainer_flagFalse_defaultsToPlatformClient() {
        // O createTokenStorage() do Android exige AndroidPlatformContext.init(context), que não
        // existe no teste unitário JVM (testDebugUnitTest); o fake evita isso sem mudar o ramo testado.
        val container = AppContainer(
            baseUrl = "https://backend.cafey.test",
            isDemoMode = false,
            customTokenStorage = FakeTokenStorage(),
        )

        assertFalse(container.isDemoMode)
        assertEquals("https://backend.cafey.test", container.baseUrl)
    }
}
