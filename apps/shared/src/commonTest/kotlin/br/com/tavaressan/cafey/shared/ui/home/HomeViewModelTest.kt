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
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

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

    private fun createApis(engine: MockEngine): Triple<DeviceApi, CommandApi, Pair<ScheduleApi, EventApi>> {
        val httpClient = HttpClient(engine, sharedHttpClientConfig("https://api.cafey.test", tokenStorage))
        val apiClient = ApiClient("https://api.cafey.test", tokenStorage, httpClient)
        return Triple(
            DeviceApi(apiClient),
            CommandApi(apiClient),
            Pair(ScheduleApi(apiClient), EventApi(apiClient)),
        )
    }

    @Test
    fun initialState_loadsAllDevices_andSelectsFirst() = runTest(testDispatcher) {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/dispositivos" -> respond(
                    content = """
                        [
                            {
                                "id": "cafey-1",
                                "nome": "Caféy Cozinha",
                                "timezone": "America/Sao_Paulo",
                                "estado": "PRONTO",
                                "online": true,
                                "versaoAgendamentos": 1,
                                "duracaoPreparoS": 300,
                                "limiarDescalcificacao": 100,
                                "contadorPreparos": 10,
                                "papel": "PROPRIETARIO",
                                "criadoEm": "2026-09-01T10:00:00Z",
                                "atualizadoEm": "2026-10-15T21:00:00Z"
                            },
                            {
                                "id": "cafey-2",
                                "nome": "Caféy Escritório",
                                "timezone": "America/Sao_Paulo",
                                "estado": "DESLIGADO",
                                "online": true,
                                "versaoAgendamentos": 1,
                                "duracaoPreparoS": 360,
                                "limiarDescalcificacao": 120,
                                "contadorPreparos": 5,
                                "papel": "CONVIDADO",
                                "criadoEm": "2026-09-05T10:00:00Z",
                                "atualizadoEm": "2026-10-15T21:00:00Z"
                            }
                        ]
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
                "/dispositivos/cafey-1/agendamentos" -> respond(content = "[]", status = HttpStatusCode.OK, headers = jsonHeaders)
                "/dispositivos/cafey-1/estatisticas" -> respond(
                    content = """{"totalPreparosConcluidos":10,"porOrigem":{},"tempoTotalPreparoSegundos":3000,"sequenciaManhasDias":3}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
                else -> respond(content = "{}", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }

        val (deviceApi, commandApi, otherApis) = createApis(engine)
        val (scheduleApi, eventApi) = otherApis
        val activeDeviceId = MutableStateFlow<String?>(null)

        val viewModel = HomeViewModel(
            deviceApi = deviceApi,
            commandApi = commandApi,
            scheduleApi = scheduleApi,
            eventApi = eventApi,
            activeDeviceId = activeDeviceId,
            coroutineScope = backgroundScope,
        )

        val state = viewModel.uiState.first { !it.loading }
        assertFalse(state.loading)
        assertEquals(2, state.devices.size)
        assertNotNull(state.device)
        assertEquals("cafey-1", state.device?.id)
        assertEquals("Caféy Cozinha", state.device?.nome)
        assertEquals("cafey-1", activeDeviceId.value)
        assertEquals(3, state.sequenciaManhasDias)
    }

    @Test
    fun selectDevice_switchesActiveDevice_andReloadsData() = runTest(testDispatcher) {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/dispositivos" -> respond(
                    content = """
                        [
                            {
                                "id": "cafey-1",
                                "nome": "Caféy Cozinha",
                                "timezone": "America/Sao_Paulo",
                                "estado": "PRONTO",
                                "online": true,
                                "versaoAgendamentos": 1,
                                "duracaoPreparoS": 300,
                                "limiarDescalcificacao": 100,
                                "contadorPreparos": 10,
                                "papel": "PROPRIETARIO",
                                "criadoEm": "2026-09-01T10:00:00Z",
                                "atualizadoEm": "2026-10-15T21:00:00Z"
                            },
                            {
                                "id": "cafey-2",
                                "nome": "Caféy Escritório",
                                "timezone": "America/Sao_Paulo",
                                "estado": "PREPARANDO",
                                "online": true,
                                "versaoAgendamentos": 1,
                                "duracaoPreparoS": 360,
                                "limiarDescalcificacao": 120,
                                "contadorPreparos": 25,
                                "papel": "CONVIDADO",
                                "criadoEm": "2026-09-05T10:00:00Z",
                                "atualizadoEm": "2026-10-15T21:00:00Z"
                            }
                        ]
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
                "/dispositivos/cafey-1/agendamentos" -> respond(content = "[]", status = HttpStatusCode.OK, headers = jsonHeaders)
                "/dispositivos/cafey-1/estatisticas" -> respond(
                    content = """{"totalPreparosConcluidos":10,"porOrigem":{},"tempoTotalPreparoSegundos":3000,"sequenciaManhasDias":3}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
                "/dispositivos/cafey-2/agendamentos" -> respond(
                    content = """
                        [
                            {
                                "id": "ag-2",
                                "dispositivoId": "cafey-2",
                                "hora": "23:59",
                                "diasSemana": 127,
                                "ativo": true,
                                "duracaoPreparoS": 360,
                                "criadoEm": "2026-09-01T10:00:00Z",
                                "atualizadoEm": "2026-10-15T10:00:00Z"
                            }
                        ]
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
                "/dispositivos/cafey-2/estatisticas" -> respond(
                    content = """{"totalPreparosConcluidos":25,"porOrigem":{},"tempoTotalPreparoSegundos":9000,"sequenciaManhasDias":7}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
                else -> respond(content = "{}", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }

        val (deviceApi, commandApi, otherApis) = createApis(engine)
        val (scheduleApi, eventApi) = otherApis
        val activeDeviceId = MutableStateFlow<String?>(null)

        val viewModel = HomeViewModel(
            deviceApi = deviceApi,
            commandApi = commandApi,
            scheduleApi = scheduleApi,
            eventApi = eventApi,
            activeDeviceId = activeDeviceId,
            coroutineScope = backgroundScope,
        )

        // Aguarda carregar cafey-1
        viewModel.uiState.first { !it.loading && it.device?.id == "cafey-1" }
        assertEquals("cafey-1", activeDeviceId.value)

        // Alterna para cafey-2
        viewModel.selectDevice("cafey-2")

        val stateAfterSwitch = viewModel.uiState.first { !it.loading && it.device?.id == "cafey-2" }
        assertEquals("cafey-2", stateAfterSwitch.device?.id)
        assertEquals("Caféy Escritório", stateAfterSwitch.device?.nome)
        assertEquals("cafey-2", activeDeviceId.value)
        assertEquals(7, stateAfterSwitch.sequenciaManhasDias)
        assertNotNull(stateAfterSwitch.proximoPreparo)
        assertEquals("ag-2", stateAfterSwitch.proximoPreparo?.agendamento?.id)
    }

    @Test
    fun externalActiveDeviceIdChange_triggersDeviceSwitchInViewModel() = runTest(testDispatcher) {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/dispositivos" -> respond(
                    content = """
                        [
                            {
                                "id": "cafey-1",
                                "nome": "Caféy Cozinha",
                                "timezone": "America/Sao_Paulo",
                                "estado": "PRONTO",
                                "online": true,
                                "versaoAgendamentos": 1,
                                "duracaoPreparoS": 300,
                                "limiarDescalcificacao": 100,
                                "contadorPreparos": 10,
                                "papel": "PROPRIETARIO",
                                "criadoEm": "2026-09-01T10:00:00Z",
                                "atualizadoEm": "2026-10-15T21:00:00Z"
                            },
                            {
                                "id": "cafey-2",
                                "nome": "Caféy Escritório",
                                "timezone": "America/Sao_Paulo",
                                "estado": "PRONTO",
                                "online": true,
                                "versaoAgendamentos": 1,
                                "duracaoPreparoS": 360,
                                "limiarDescalcificacao": 120,
                                "contadorPreparos": 5,
                                "papel": "CONVIDADO",
                                "criadoEm": "2026-09-05T10:00:00Z",
                                "atualizadoEm": "2026-10-15T21:00:00Z"
                            }
                        ]
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
                else -> respond(content = "{}", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }

        val (deviceApi, commandApi, otherApis) = createApis(engine)
        val (scheduleApi, eventApi) = otherApis
        val activeDeviceId = MutableStateFlow<String?>(null)

        val viewModel = HomeViewModel(
            deviceApi = deviceApi,
            commandApi = commandApi,
            scheduleApi = scheduleApi,
            eventApi = eventApi,
            activeDeviceId = activeDeviceId,
            coroutineScope = backgroundScope,
        )

        // Inicialmente cafeteria 1
        viewModel.uiState.first { !it.loading && it.device?.id == "cafey-1" }

        // Alteração externa (ex.: de outra tela ou menu)
        activeDeviceId.value = "cafey-2"

        val stateAfter = viewModel.uiState.first { !it.loading && it.device?.id == "cafey-2" }
        assertEquals("cafey-2", stateAfter.device?.id)
        assertEquals("Caféy Escritório", stateAfter.device?.nome)
    }
}
