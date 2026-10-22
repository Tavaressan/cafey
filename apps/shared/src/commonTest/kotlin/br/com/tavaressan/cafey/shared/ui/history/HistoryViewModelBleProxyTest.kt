package br.com.tavaressan.cafey.shared.ui.history

import br.com.tavaressan.cafey.shared.auth.FakeTokenStorage
import br.com.tavaressan.cafey.shared.ble.FakeBleClient
import br.com.tavaressan.cafey.shared.domain.model.BleEvento
import br.com.tavaressan.cafey.shared.network.ApiClient
import br.com.tavaressan.cafey.shared.network.DeviceApi
import br.com.tavaressan.cafey.shared.network.EventApi
import br.com.tavaressan.cafey.shared.network.sharedHttpClientConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelBleProxyTest {

    @Test
    fun init_quandoHaEventosPendentes_sincronizaViaBleAntesDeListar() = runTest {
        var proxyBleChamado = false
        val pendente = BleEvento(
            eventoId = "evt-offline-1",
            resultado = "CONCLUIDO",
            origem = "BOTAO",
            duracaoS = 30,
            timestamp = "2026-10-22T10:00:00Z",
        )

        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path == "/dispositivos" -> {
                    respond(
                        content = """[{"id":"dev-1","nome":"Caféy","timezone":"America/Sao_Paulo","estado":"PRONTO","online":true,"versaoAgendamentos":1,"duracaoPreparoS":480,"limiarDescalcificacao":100,"contadorPreparos":10,"papel":"PROPRIETARIO","criadoEm":"2026-10-15T10:00:00Z","atualizadoEm":"2026-10-15T10:00:00Z"}]""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                path == "/dispositivos/dev-1/eventos/proxy-ble" -> {
                    proxyBleChamado = true
                    respond(
                        content = """[{"id":"evt-server-1","eventoId":"evt-offline-1","tipo":"PREPARO","resultado":"CONCLUIDO","origem":"BOTAO","duracaoS":30,"timestamp":"2026-10-22T10:00:00Z","criadoEm":"2026-10-22T10:01:00Z"}]""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                path == "/dispositivos/dev-1/estatisticas" -> {
                    respond(
                        content = """{"totalPreparosConcluidos":1,"porOrigem":{"BOTAO":1},"tempoTotalPreparoSegundos":30,"sequenciaManhasDias":1}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                path == "/dispositivos/dev-1/eventos" -> {
                    respond(
                        content = """{"content":[{"id":"evt-server-1","eventoId":"evt-offline-1","tipo":"PREPARO","resultado":"CONCLUIDO","origem":"BOTAO","duracaoS":30,"timestamp":"2026-10-22T10:00:00Z","criadoEm":"2026-10-22T10:01:00Z"}],"totalElements":1,"totalPages":1,"size":20,"number":0,"first":true,"last":true,"empty":false}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                else -> respond(content = "{}", status = HttpStatusCode.OK)
            }
        }

        val tokenStorage = FakeTokenStorage()
        val client = HttpClient(engine, sharedHttpClientConfig("https://api.cafey.test", tokenStorage))
        val apiClient = ApiClient("https://api.cafey.test", tokenStorage, client)

        val fakeBle = FakeBleClient(eventosPendentes = listOf(pendente))

        val viewModel = HistoryViewModel(
            deviceApi = DeviceApi(apiClient),
            eventApi = EventApi(apiClient),
            bleClient = fakeBle,
            coroutineScope = backgroundScope,
        )

        val state = viewModel.uiState.first { !it.loading }

        assertTrue(proxyBleChamado, "Deveria ter enviado os eventos pendentes para /proxy-ble")
        assertEquals(listOf("dev-1" to listOf("evt-offline-1")), fakeBle.confirmacoes)
        assertEquals(1, state.eventos.size)
        assertEquals("evt-offline-1", state.eventos.first().eventoId)
        assertNull(state.errorMessage)
    }

    @Test
    fun init_quandoBleFalha_carregaEventosDaNuvemSemTravar() = runTest {
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path == "/dispositivos" -> {
                    respond(
                        content = """[{"id":"dev-1","nome":"Caféy","timezone":"America/Sao_Paulo","estado":"PRONTO","online":true,"versaoAgendamentos":1,"duracaoPreparoS":480,"limiarDescalcificacao":100,"contadorPreparos":10,"papel":"PROPRIETARIO","criadoEm":"2026-10-15T10:00:00Z","atualizadoEm":"2026-10-15T10:00:00Z"}]""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                path == "/dispositivos/dev-1/estatisticas" -> {
                    respond(
                        content = """{"totalPreparosConcluidos":0,"porOrigem":{},"tempoTotalPreparoSegundos":0,"sequenciaManhasDias":0}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                path == "/dispositivos/dev-1/eventos" -> {
                    respond(
                        content = """{"content":[],"totalElements":0,"totalPages":0,"size":20,"number":0,"first":true,"last":true,"empty":true}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                else -> respond(content = "{}", status = HttpStatusCode.OK)
            }
        }

        val tokenStorage = FakeTokenStorage()
        val client = HttpClient(engine, sharedHttpClientConfig("https://api.cafey.test", tokenStorage))
        val apiClient = ApiClient("https://api.cafey.test", tokenStorage, client)

        val fakeBle = FakeBleClient(falharAoConectar = true)

        val viewModel = HistoryViewModel(
            deviceApi = DeviceApi(apiClient),
            eventApi = EventApi(apiClient),
            bleClient = fakeBle,
            coroutineScope = backgroundScope,
        )

        val state = viewModel.uiState.first { !it.loading }

        assertEquals(0, state.eventos.size)
        assertNull(state.errorMessage)
    }
}
