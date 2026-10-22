package br.com.tavaressan.cafey.shared.ui.home

import br.com.tavaressan.cafey.shared.auth.FakeTokenStorage
import br.com.tavaressan.cafey.shared.ble.FakeBleClient
import br.com.tavaressan.cafey.shared.domain.model.AcaoComando
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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelBleFallbackTest {

    private fun criarViewModel(
        commandFailsWithNetwork: Boolean = false,
        bleClient: FakeBleClient? = null,
        coroutineScope: kotlinx.coroutines.CoroutineScope,
    ): HomeViewModel {
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
                path == "/dispositivos/dev-1/agendamentos" -> {
                    respond(
                        content = "[]",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                path == "/dispositivos/dev-1/estatisticas" -> {
                    respond(
                        content = """{"totalPreparosConcluidos":10,"porOrigem":{"APP":10},"tempoTotalPreparoSegundos":4800,"sequenciaManhasDias":3}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                path == "/dispositivos/dev-1/comando" -> {
                    if (commandFailsWithNetwork) {
                        throw io.ktor.client.network.sockets.ConnectTimeoutException("Connect timed out")
                    } else {
                        respond(
                            content = """{"comandoId":"cmd-1","acao":"LIGAR","duracaoS":480,"emitidoEm":"2026-10-15T10:00:00Z"}""",
                            status = HttpStatusCode.Accepted,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }
                else -> respond(content = "{}", status = HttpStatusCode.OK)
            }
        }

        val tokenStorage = FakeTokenStorage()
        val client = HttpClient(engine, sharedHttpClientConfig("https://api.cafey.test", tokenStorage))
        val apiClient = ApiClient("https://api.cafey.test", tokenStorage, client)

        return HomeViewModel(
            deviceApi = DeviceApi(apiClient),
            commandApi = CommandApi(apiClient),
            scheduleApi = ScheduleApi(apiClient),
            eventApi = EventApi(apiClient),
            bleClient = bleClient,
            coroutineScope = coroutineScope,
        )
    }

    @Test
    fun ligar_quandoRedeOk_enviaPorNuvemENaoUsaBle() = runTest {
        val fakeBle = FakeBleClient()
        val viewModel = criarViewModel(commandFailsWithNetwork = false, bleClient = fakeBle, coroutineScope = backgroundScope)

        viewModel.uiState.first { !it.loading }
        viewModel.ligar()
        viewModel.uiState.first { !it.commandInFlight }

        assertTrue(fakeBle.comandosEnviados.isEmpty())
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun ligar_quandoRedeFalha_caiParaBleEAtualizaEstado() = runTest {
        val fakeBle = FakeBleClient()
        val viewModel = criarViewModel(commandFailsWithNetwork = true, bleClient = fakeBle, coroutineScope = backgroundScope)

        viewModel.uiState.first { !it.loading }
        viewModel.ligar()
        viewModel.uiState.first { !it.commandInFlight }

        assertEquals(1, fakeBle.comandosEnviados.size)
        assertEquals("dev-1" to AcaoComando.LIGAR, fakeBle.comandosEnviados.first())
        assertEquals("PREPARANDO", viewModel.uiState.value.device?.estado)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun ligar_quandoRedeEFallbackBleFalham_exibeErro() = runTest {
        val fakeBle = FakeBleClient(falharAoConectar = true)
        val viewModel = criarViewModel(commandFailsWithNetwork = true, bleClient = fakeBle, coroutineScope = backgroundScope)

        viewModel.uiState.first { !it.loading }
        viewModel.ligar()
        val erroState = viewModel.uiState.first { it.errorMessage != null }

        assertTrue(fakeBle.comandosEnviados.isEmpty())
        assertEquals("Nenhuma base BLE encontrada nas proximidades", erroState.errorMessage)
    }
}
