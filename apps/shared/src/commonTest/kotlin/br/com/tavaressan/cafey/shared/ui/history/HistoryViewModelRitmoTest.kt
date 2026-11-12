package br.com.tavaressan.cafey.shared.ui.history

import br.com.tavaressan.cafey.shared.auth.FakeTokenStorage
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelRitmoTest {

    @Test
    fun init_naoBuscaEventosAntesDeAbrirHistorico() = runTest {
        var eventosChamado = false
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path == "/dispositivos" -> respond(
                    content = """[{"id":"dev-1","nome":"Caféy","timezone":"America/Sao_Paulo","estado":"PRONTO","online":true,"versaoAgendamentos":1,"duracaoPreparoS":480,"limiarDescalcificacao":100,"contadorPreparos":10,"papel":"PROPRIETARIO","criadoEm":"2026-10-15T10:00:00Z","atualizadoEm":"2026-10-15T10:00:00Z"}]""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                path == "/dispositivos/dev-1/estatisticas" -> respond(
                    content = """{"totalPreparosConcluidos":42,"porOrigem":{"APP":42},"tempoTotalPreparoSegundos":20160,"sequenciaManhasDias":5,"porDiaDaSemana":[8,5,7,6,9,4,3]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                path == "/dispositivos/dev-1/eventos" -> {
                    eventosChamado = true
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

        val viewModel = HistoryViewModel(
            deviceApi = DeviceApi(apiClient),
            eventApi = EventApi(apiClient),
            coroutineScope = backgroundScope,
        )

        val state = viewModel.uiState.first { !it.loading }

        assertFalse(state.historicoExpandido)
        assertFalse(state.eventosCarregados)
        assertTrue(state.eventos.isEmpty())
        assertFalse(eventosChamado, "Não deve buscar eventos antes de abrir o histórico")
    }

    @Test
    fun alternarHistorico_expandeRecolheEBuscaEventosNaPrimeiraAbertura() = runTest {
        var eventosChamadas = 0
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path == "/dispositivos" -> respond(
                    content = """[{"id":"dev-1","nome":"Caféy","timezone":"America/Sao_Paulo","estado":"PRONTO","online":true,"versaoAgendamentos":1,"duracaoPreparoS":480,"limiarDescalcificacao":100,"contadorPreparos":10,"papel":"PROPRIETARIO","criadoEm":"2026-10-15T10:00:00Z","atualizadoEm":"2026-10-15T10:00:00Z"}]""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                path == "/dispositivos/dev-1/estatisticas" -> respond(
                    content = """{"totalPreparosConcluidos":1,"porOrigem":{"APP":1},"tempoTotalPreparoSegundos":480,"sequenciaManhasDias":1,"porDiaDaSemana":[1,0,0,0,0,0,0]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                path == "/dispositivos/dev-1/eventos" -> {
                    eventosChamadas++
                    respond(
                        content = """{"content":[{"id":"evt-1","eventoId":"e1","tipo":"PREPARO","resultado":"CONCLUIDO","origem":"APP","duracaoS":480,"timestamp":"2026-10-20T10:00:00Z","criadoEm":"2026-10-20T10:08:00Z"}],"totalElements":1,"totalPages":1,"size":20,"number":0,"first":true,"last":true,"empty":false}""",
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

        val viewModel = HistoryViewModel(
            deviceApi = DeviceApi(apiClient),
            eventApi = EventApi(apiClient),
            coroutineScope = backgroundScope,
        )

        viewModel.uiState.first { !it.loading }
        viewModel.alternarHistorico()
        val aberto = viewModel.uiState.first { it.eventosCarregados }
        assertTrue(aberto.historicoExpandido)
        assertEquals(1, aberto.eventos.size)
        assertEquals(1, eventosChamadas)

        viewModel.alternarHistorico()
        val recolhido = viewModel.uiState.first { !it.historicoExpandido }
        assertFalse(recolhido.historicoExpandido)
        assertEquals(1, eventosChamadas, "Não deve buscar eventos de novo ao recolher/reabrir")

        viewModel.alternarHistorico()
        val reaberto = viewModel.uiState.first { it.historicoExpandido }
        assertTrue(reaberto.historicoExpandido)
        assertEquals(1, eventosChamadas, "Não deve buscar eventos de novo ao reabrir")
    }

    @Test
    fun trocarDispositivoComHistoricoAberto_recarregaEventosDoNovoDispositivo() = runTest {
        val activeDeviceId = MutableStateFlow<String?>("dev-1")
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path == "/dispositivos" -> respond(
                    content = """[
                        {"id":"dev-1","nome":"Um","timezone":"America/Sao_Paulo","estado":"PRONTO","online":true,"versaoAgendamentos":1,"duracaoPreparoS":480,"limiarDescalcificacao":100,"contadorPreparos":10,"papel":"PROPRIETARIO","criadoEm":"2026-10-15T10:00:00Z","atualizadoEm":"2026-10-15T10:00:00Z"},
                        {"id":"dev-2","nome":"Dois","timezone":"America/Sao_Paulo","estado":"PRONTO","online":true,"versaoAgendamentos":1,"duracaoPreparoS":480,"limiarDescalcificacao":100,"contadorPreparos":5,"papel":"PROPRIETARIO","criadoEm":"2026-10-15T10:00:00Z","atualizadoEm":"2026-10-15T10:00:00Z"}
                    ]""".trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                path == "/dispositivos/dev-1/estatisticas" -> respond(
                    content = """{"totalPreparosConcluidos":1,"porOrigem":{"APP":1},"tempoTotalPreparoSegundos":480,"sequenciaManhasDias":1,"porDiaDaSemana":[1,0,0,0,0,0,0]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                path == "/dispositivos/dev-1/eventos" -> respond(
                    content = """{"content":[{"id":"evt-1","eventoId":"e1","tipo":"PREPARO","resultado":"CONCLUIDO","origem":"APP","duracaoS":480,"timestamp":"2026-10-20T10:00:00Z","criadoEm":"2026-10-20T10:08:00Z"}],"totalElements":1,"totalPages":1,"size":20,"number":0,"first":true,"last":true,"empty":false}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                path == "/dispositivos/dev-2/estatisticas" -> respond(
                    content = """{"totalPreparosConcluidos":2,"porOrigem":{"BOTAO":2},"tempoTotalPreparoSegundos":600,"sequenciaManhasDias":0,"porDiaDaSemana":[0,1,1,0,0,0,0]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                path == "/dispositivos/dev-2/eventos" -> respond(
                    content = """{"content":[{"id":"evt-2","eventoId":"e2","tipo":"PREPARO","resultado":"CONCLUIDO","origem":"BOTAO","duracaoS":300,"timestamp":"2026-10-21T10:00:00Z","criadoEm":"2026-10-21T10:05:00Z"},{"id":"evt-3","eventoId":"e3","tipo":"PREPARO","resultado":"CONCLUIDO","origem":"BOTAO","duracaoS":300,"timestamp":"2026-10-22T10:00:00Z","criadoEm":"2026-10-22T10:05:00Z"}],"totalElements":2,"totalPages":1,"size":20,"number":0,"first":true,"last":true,"empty":false}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> respond(content = "{}", status = HttpStatusCode.OK)
            }
        }

        val tokenStorage = FakeTokenStorage()
        val client = HttpClient(engine, sharedHttpClientConfig("https://api.cafey.test", tokenStorage))
        val apiClient = ApiClient("https://api.cafey.test", tokenStorage, client)

        val viewModel = HistoryViewModel(
            deviceApi = DeviceApi(apiClient),
            eventApi = EventApi(apiClient),
            bleClient = null,
            activeDeviceId = activeDeviceId,
            coroutineScope = backgroundScope,
        )

        viewModel.uiState.first { !it.loading }
        viewModel.alternarHistorico()
        viewModel.uiState.first { it.eventosCarregados && it.eventos.any { ev -> ev.id == "evt-1" } }

        activeDeviceId.value = "dev-2"

        val aposTroca = viewModel.uiState.first {
            it.deviceId == "dev-2" && it.eventosCarregados && it.eventos.any { ev -> ev.id == "evt-2" }
        }

        assertTrue(aposTroca.historicoExpandido)
        assertEquals(2, aposTroca.eventos.size)
        assertEquals(listOf("evt-2", "evt-3"), aposTroca.eventos.map { it.id })
    }
}
