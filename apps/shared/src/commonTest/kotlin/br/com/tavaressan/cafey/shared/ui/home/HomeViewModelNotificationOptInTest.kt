package br.com.tavaressan.cafey.shared.ui.home

import br.com.tavaressan.cafey.shared.auth.FakeTokenStorage
import br.com.tavaressan.cafey.shared.auth.StoredTokens
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelNotificationOptInTest {

    private val testDispatcher = StandardTestDispatcher()
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val tokenStorage = FakeTokenStorage(StoredTokens("access", "refresh"))

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun semDetector_naoConsultaEventosNoPolling() = runTest(testDispatcher) {
        var eventosCallCount = 0
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath == "/dispositivos" -> respond(
                    content = """
                        [
                          {
                            "id":"cafey-1","nome":"Caféy Cozinha","timezone":"America/Sao_Paulo",
                            "estado":"PRONTO","online":true,"versaoAgendamentos":1,"duracaoPreparoS":300,
                            "limiarDescalcificacao":100,"contadorPreparos":10,"papel":"PROPRIETARIO",
                            "criadoEm":"2026-09-01T10:00:00Z","atualizadoEm":"2026-10-15T21:00:00Z"
                          }
                        ]
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
                request.url.encodedPath.endsWith("/agendamentos") -> respond("[]", HttpStatusCode.OK, jsonHeaders)
                request.url.encodedPath.endsWith("/estatisticas") -> respond(
                    """{"totalPreparosConcluidos":0,"porOrigem":{},"tempoTotalPreparoSegundos":0,"sequenciaManhasDias":0}""",
                    HttpStatusCode.OK,
                    jsonHeaders,
                )
                request.url.encodedPath.endsWith("/eventos") -> {
                    eventosCallCount++
                    respond(
                        """{"content":[],"totalElements":0,"totalPages":0,"number":0,"size":5,"last":true}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                }
                else -> respond("{}", HttpStatusCode.OK, jsonHeaders)
            }
        }

        val httpClient = HttpClient(engine, sharedHttpClientConfig("https://api.cafey.test", tokenStorage))
        val apiClient = ApiClient("https://api.cafey.test", tokenStorage, httpClient)
        val viewModel = HomeViewModel(
            deviceApi = DeviceApi(apiClient),
            commandApi = CommandApi(apiClient),
            scheduleApi = ScheduleApi(apiClient),
            eventApi = EventApi(apiClient),
            coroutineScope = backgroundScope,
        )

        viewModel.uiState.first { !it.loading }
        // Pollings manuais adicionais não devem buscar eventos sem detector.
        viewModel.refresh()
        viewModel.refresh()

        assertEquals(0, eventosCallCount)
        assertFalse(viewModel.uiState.value.devices.isEmpty())
    }
}
