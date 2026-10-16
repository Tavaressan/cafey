package br.com.tavaressan.cafey.shared.ui.device

import br.com.tavaressan.cafey.shared.auth.FakeTokenStorage
import br.com.tavaressan.cafey.shared.network.ApiClient
import br.com.tavaressan.cafey.shared.network.DeviceApi
import br.com.tavaressan.cafey.shared.network.sharedHttpClientConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceFormViewModelTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private val deviceJson = """
        {
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
        }
    """.trimIndent()

    /** Método, caminho e corpo JSON de cada requisição que o ViewModel fez. */
    private data class Chamada(val method: String, val path: String, val body: JsonObject?)

    private fun deviceApi(chamadas: MutableList<Chamada>): DeviceApi {
        val engine = MockEngine { request ->
            val texto = request.body.toByteArray().decodeToString()
            val body = if (texto.isBlank()) null else Json.parseToJsonElement(texto).jsonObject
            chamadas += Chamada(request.method.value, request.url.encodedPath, body)
            when (request.method.value) {
                "DELETE" -> respond("", HttpStatusCode.NoContent)
                "POST" -> respond(deviceJson, HttpStatusCode.Created, jsonHeaders)
                else -> respond(deviceJson, HttpStatusCode.OK, jsonHeaders)
            }
        }
        val tokenStorage = FakeTokenStorage()
        val client = HttpClient(engine, sharedHttpClientConfig("https://api.cafey.test", tokenStorage))
        return DeviceApi(ApiClient("https://api.cafey.test", tokenStorage, client))
    }

    @Test
    fun cadastro_blankOptionalFields_areOmittedFromTheRequest() = runTest {
        val chamadas = mutableListOf<Chamada>()
        val viewModel = DeviceFormViewModel(deviceApi(chamadas), deviceId = null, coroutineScope = backgroundScope)

        viewModel.onNomeChange("Cafeteira Nova")
        viewModel.onTimezoneChange("")
        viewModel.onDuracaoPreparoSChange("")
        viewModel.onLimiarDescalcificacaoChange("")
        viewModel.gravar()
        viewModel.uiState.first { it.success }

        val chamada = chamadas.single()
        assertEquals("POST", chamada.method)
        assertEquals("/dispositivos", chamada.path)
        // Campo ausente = null no backend, que aplica o padrão do Dispositivo.
        assertEquals(setOf("nome"), chamada.body!!.keys)
        assertEquals("Cafeteira Nova", chamada.body["nome"]!!.jsonPrimitive.content)
    }

    @Test
    fun edicao_loadsTheDeviceAndSendsPut() = runTest {
        val chamadas = mutableListOf<Chamada>()
        val viewModel = DeviceFormViewModel(deviceApi(chamadas), deviceId = "dev-1", coroutineScope = backgroundScope)

        val carregado = viewModel.uiState.first { !it.loading }
        assertEquals("Cafeteira Cozinha", carregado.nome)
        assertEquals("240", carregado.duracaoPreparoS)
        assertEquals("400", carregado.limiarDescalcificacao)

        viewModel.onDuracaoPreparoSChange("600")
        viewModel.gravar()
        viewModel.uiState.first { it.success }

        val put = chamadas.last()
        assertEquals("PUT", put.method)
        assertEquals("/dispositivos/dev-1", put.path)
        assertEquals("600", put.body!!["duracaoPreparoS"]!!.jsonPrimitive.content)
    }

    @Test
    fun limparCampos_emptiesTheFormAndKeepsEditingTheSameDevice() = runTest {
        val viewModel = DeviceFormViewModel(deviceApi(mutableListOf()), deviceId = "dev-1", coroutineScope = backgroundScope)
        viewModel.uiState.first { !it.loading }

        viewModel.limparCampos()

        val state = viewModel.uiState.value
        assertEquals("dev-1", state.deviceId)
        assertEquals("", state.nome)
        assertEquals("", state.timezone)
        assertEquals("", state.duracaoPreparoS)
        assertEquals("", state.limiarDescalcificacao)
    }

    @Test
    fun gravar_withInvalidFields_doesNotCallTheApi() = runTest {
        val chamadas = mutableListOf<Chamada>()
        val viewModel = DeviceFormViewModel(deviceApi(chamadas), deviceId = null, coroutineScope = backgroundScope)

        viewModel.limparCampos()
        viewModel.onDuracaoPreparoSChange("5")
        viewModel.gravar()

        val state = viewModel.uiState.value
        assertFalse(state.errors.isValid)
        assertFalse(state.saving)
        assertTrue(chamadas.isEmpty())
    }

    @Test
    fun excluir_sendsDeleteAndFinishes() = runTest {
        val chamadas = mutableListOf<Chamada>()
        val viewModel = DeviceFormViewModel(deviceApi(chamadas), deviceId = "dev-1", coroutineScope = backgroundScope)
        viewModel.uiState.first { !it.loading }

        viewModel.excluir()
        viewModel.uiState.first { it.success }

        val delete = chamadas.last()
        assertEquals("DELETE", delete.method)
        assertEquals("/dispositivos/dev-1", delete.path)
    }
}
