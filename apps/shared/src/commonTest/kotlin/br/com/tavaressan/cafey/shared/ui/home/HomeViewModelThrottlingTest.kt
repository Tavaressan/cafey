package br.com.tavaressan.cafey.shared.ui.home

import br.com.tavaressan.cafey.shared.auth.FakeTokenStorage
import br.com.tavaressan.cafey.shared.network.ApiClient
import br.com.tavaressan.cafey.shared.network.CommandApi
import br.com.tavaressan.cafey.shared.network.DeviceApi
import br.com.tavaressan.cafey.shared.network.EventApi
import br.com.tavaressan.cafey.shared.network.ScheduleApi
import br.com.tavaressan.cafey.shared.network.sharedHttpClientConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Validação de Throttling do endpoint /estatisticas em HomeViewModel (Issue #199).
 *
 * Garante que a métrica de sequência de manhãs não gere rajada de requisições analíticas
 * no polling rápido de 5 segundos de estado do dispositivo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelThrottlingTest {

    @Test
    fun estatisticasCall_isThrottled_duringRapidPolling() = runTest {
        var deviceCallCount = 0
        var statsCallCount = 0

        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path == "/dispositivos" -> {
                    deviceCallCount++
                    respond(
                        content = """[{"id":"dev-1","nome":"Caféy","timezone":"America/Sao_Paulo","estado":"PRONTO","online":true,"versaoAgendamentos":1,"duracaoPreparoS":480,"limiarDescalcificacao":100,"contadorPreparos":10,"papel":"PROPRIETARIO","criadoEm":"2026-10-15T10:00:00Z","atualizadoEm":"2026-10-15T10:00:00Z"}]""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                path == "/dispositivos/dev-1/agendamentos" -> {
                    respond(
                        content = "[]",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                path == "/dispositivos/dev-1/estatisticas" -> {
                    statsCallCount++
                    respond(
                        content = """{"totalPreparosConcluidos":10,"porOrigem":{"APP":10},"tempoTotalPreparoSegundos":4800,"sequenciaManhasDias":3}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                path == "/dispositivos/dev-1/comando" -> {
                    respond(
                        content = """{"comandoId":"cmd-1","acao":"LIGAR","duracaoS":480,"emitidoEm":"2026-10-15T10:00:00Z"}""",
                        status = HttpStatusCode.Accepted,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                else -> respond(content = "{}", status = HttpStatusCode.OK)
            }
        }

        val tokenStorage = FakeTokenStorage()
        val client = HttpClient(engine, sharedHttpClientConfig("https://api.cafey.test", tokenStorage))
        val apiClient = ApiClient("https://api.cafey.test", tokenStorage, client)

        val viewModel = HomeViewModel(
            deviceApi = DeviceApi(apiClient),
            commandApi = CommandApi(apiClient),
            scheduleApi = ScheduleApi(apiClient),
            eventApi = EventApi(apiClient),
            coroutineScope = backgroundScope,
        )

        // 1. Carga inicial
        val initial = viewModel.uiState.first { !it.loading }
        assertEquals(1, statsCallCount, "Primeira carga deve buscar /estatisticas para preencher sequenciaManhasDias")
        assertEquals(1, deviceCallCount)
        assertEquals(3, initial.sequenciaManhasDias)

        // 2. Múltiplos ciclos de polling rápido dentro do intervalo de 60s
        repeat(5) {
            viewModel.refresh()
        }

        assertTrue(deviceCallCount >= 6, "Dispositivo deve ter sido consultado pelo menos 6 vezes")
        assertEquals(1, statsCallCount, "Chamadas a /estatisticas devem permanecer contidas em 1 pelo throttle de 60s")

        // 3. Execução de comando deve invalidar throttle para capturar novo estado de preparo
        viewModel.ligar()
        viewModel.uiState.first { !it.commandInFlight }
        assertEquals(2, statsCallCount, "Comando executado deve forçar revalidação imediata de estatísticas")
    }
}
