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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** O aviso de falha de conexão da sondagem não pode ficar preso depois que o servidor volta. */
class HomeViewModelErroSondagemTest {

    @Test
    fun erroDaSondagemSomeQuandoAProximaConsultaFunciona() = runTest {
        var servidorForaDoAr = false
        val engine = MockEngine { request ->
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            when (request.url.encodedPath) {
                "/dispositivos" -> {
                    if (servidorForaDoAr) throw io.ktor.client.network.sockets.ConnectTimeoutException("timeout")
                    respond(
                        """[{"id":"dev-1","nome":"Caféy","timezone":"America/Sao_Paulo","estado":"PRONTO","online":true,"versaoAgendamentos":1,"duracaoPreparoS":480,"limiarDescalcificacao":100,"contadorPreparos":10,"papel":"PROPRIETARIO","criadoEm":"2026-10-15T10:00:00Z","atualizadoEm":"2026-10-15T10:00:00Z"}]""",
                        HttpStatusCode.OK, json,
                    )
                }
                "/dispositivos/dev-1/estatisticas" -> respond(
                    """{"totalPreparosConcluidos":10,"porOrigem":{"APP":10},"tempoTotalPreparoSegundos":4800,"sequenciaManhasDias":3}""",
                    HttpStatusCode.OK, json,
                )
                "/dispositivos/dev-1/agendamentos" -> respond("[]", HttpStatusCode.OK, json)
                else -> respond("{}", HttpStatusCode.OK, json)
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

        viewModel.uiState.first { !it.loading && it.device != null }

        servidorForaDoAr = true
        viewModel.refresh()
        assertNotNull(viewModel.uiState.value.errorMessage)

        servidorForaDoAr = false
        viewModel.refresh()
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals("dev-1", viewModel.uiState.value.device?.id)
    }
}
