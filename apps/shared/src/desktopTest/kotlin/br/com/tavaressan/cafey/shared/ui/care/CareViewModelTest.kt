package br.com.tavaressan.cafey.shared.ui.care

import br.com.tavaressan.cafey.shared.auth.FakeTokenStorage
import br.com.tavaressan.cafey.shared.domain.model.TipoCuidado
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CareViewModelTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private val sampleDeviceListJson = """
        [{
            "id":"dev-1",
            "nome":"Cafeteira Cozinha",
            "timezone":"America/Sao_Paulo",
            "estado":"PRONTO",
            "online":true,
            "versaoAgendamentos":1,
            "duracaoPreparoS":240,
            "limiarDescalcificacao":400,
            "contadorPreparos":60,
            "papel":"PROPRIETARIO",
            "criadoEm":"2026-01-01T00:00:00Z",
            "atualizadoEm":"2026-01-01T00:00:00Z"
        }]
    """.trimIndent()

    private val sampleCuidadosJson = """
        {
            "descalcificacao":{"contadorPreparos":60,"limiarDescalcificacao":400,"precisaDescalcificar":false,"percentualUso":15.0},
            "enxague":{"contadorPreparos":34,"limiarPreparos":40,"precisaAtencao":false,"percentualUso":85.0},
            "filtro":{"contadorPreparos":210,"limiarPreparos":300,"precisaAtencao":false,"percentualUso":70.0},
            "destaque":"ENXAGUE"
        }
    """.trimIndent()

    private val sampleEnxagueBaixaJson = """
        {"contadorPreparos":0,"limiarPreparos":40,"precisaAtencao":false,"percentualUso":0.0}
    """.trimIndent()

    @Test
    fun initialLoadSuccess_populatesCuidados() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/dispositivos" -> respond(sampleDeviceListJson, HttpStatusCode.OK, jsonHeaders)
                "/dispositivos/dev-1/cuidados" -> respond(sampleCuidadosJson, HttpStatusCode.OK, jsonHeaders)
                else -> respond("", HttpStatusCode.NotFound)
            }
        }

        val tokenStorage = FakeTokenStorage()
        val client = HttpClient(engine, sharedHttpClientConfig("https://api.cafey.test", tokenStorage))
        val apiClient = ApiClient("https://api.cafey.test", tokenStorage, client)
        val viewModel = CareViewModel(DeviceApi(apiClient), EventApi(apiClient), coroutineScope = backgroundScope)

        val state = viewModel.uiState.first { !it.loading }
        assertEquals(false, state.loading)
        assertEquals("dev-1", state.deviceId)
        assertTrue(state.podeDarBaixa)
        assertNotNull(state.cuidados)
        assertEquals(34, state.cuidados.enxague.contadorPreparos)
        assertNull(state.errorMessage)
    }

    @Test
    fun initialLoadFailure_leavesCuidadosNullAndSetsErrorMessage() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/dispositivos" -> respond(sampleDeviceListJson, HttpStatusCode.OK, jsonHeaders)
                "/dispositivos/dev-1/cuidados" -> respond("Erro de cliente", HttpStatusCode.BadRequest)
                else -> respond("", HttpStatusCode.NotFound)
            }
        }

        val tokenStorage = FakeTokenStorage()
        val client = HttpClient(engine, sharedHttpClientConfig("https://api.cafey.test", tokenStorage))
        val apiClient = ApiClient("https://api.cafey.test", tokenStorage, client)
        val viewModel = CareViewModel(DeviceApi(apiClient), EventApi(apiClient), coroutineScope = backgroundScope)

        val state = viewModel.uiState.first { !it.loading }
        assertEquals(false, state.loading)
        assertNull(state.cuidados)
        assertNotNull(state.errorMessage)
    }

    @Test
    fun retryAfterFailure_reloadsCuidadosSuccessfully() = runTest {
        var failCuidados = true
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/dispositivos" -> respond(sampleDeviceListJson, HttpStatusCode.OK, jsonHeaders)
                "/dispositivos/dev-1/cuidados" -> {
                    if (failCuidados) {
                        respond("Erro de cliente", HttpStatusCode.BadRequest)
                    } else {
                        respond(sampleCuidadosJson, HttpStatusCode.OK, jsonHeaders)
                    }
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }

        val tokenStorage = FakeTokenStorage()
        val client = HttpClient(engine, sharedHttpClientConfig("https://api.cafey.test", tokenStorage))
        val apiClient = ApiClient("https://api.cafey.test", tokenStorage, client)
        val viewModel = CareViewModel(DeviceApi(apiClient), EventApi(apiClient), coroutineScope = backgroundScope)

        val initialErrorState = viewModel.uiState.first { !it.loading }
        assertNull(initialErrorState.cuidados)
        assertNotNull(initialErrorState.errorMessage)

        // Recupera a conexão e aciona retry
        failCuidados = false
        viewModel.retry()

        val reloadedState = viewModel.uiState.first { !it.loading && it.cuidados != null }
        assertEquals(false, reloadedState.loading)
        assertNotNull(reloadedState.cuidados)
        assertNull(reloadedState.errorMessage)
    }

    @Test
    fun darBaixaUpdatesCardLocallyEvenIfObterCuidadosFails() = runTest {
        var failReload = false
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/dispositivos" -> respond(sampleDeviceListJson, HttpStatusCode.OK, jsonHeaders)
                "/dispositivos/dev-1/cuidados" -> {
                    if (failReload) {
                        respond("Falha na recarga", HttpStatusCode.BadRequest)
                    } else {
                        respond(sampleCuidadosJson, HttpStatusCode.OK, jsonHeaders)
                    }
                }
                "/dispositivos/dev-1/enxague/baixa" -> respond(sampleEnxagueBaixaJson, HttpStatusCode.OK, jsonHeaders)
                else -> respond("", HttpStatusCode.NotFound)
            }
        }

        val tokenStorage = FakeTokenStorage()
        val client = HttpClient(engine, sharedHttpClientConfig("https://api.cafey.test", tokenStorage))
        val apiClient = ApiClient("https://api.cafey.test", tokenStorage, client)
        val viewModel = CareViewModel(DeviceApi(apiClient), EventApi(apiClient), coroutineScope = backgroundScope)

        val readyState = viewModel.uiState.first { !it.loading }
        assertEquals(34, readyState.cuidados?.enxague?.contadorPreparos)

        // Configura recarga para falhar após o POST da baixa ter sucesso
        failReload = true
        viewModel.darBaixa(TipoCuidado.ENXAGUE)

        val state = viewModel.uiState.first { it.baixaInFlight == null && it.errorMessage != null }
        // Baixa foi registrada localmente com sucesso: contador zerado
        assertEquals(0, state.cuidados?.enxague?.contadorPreparos)
        // baixaInFlight é liberado
        assertNull(state.baixaInFlight)
        // Mensagem de aviso informa problema na atualização do resumo
        assertEquals("Não foi possível atualizar o resumo de cuidados.", state.errorMessage)
    }
}
