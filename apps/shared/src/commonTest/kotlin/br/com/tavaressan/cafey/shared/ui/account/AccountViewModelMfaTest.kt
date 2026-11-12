package br.com.tavaressan.cafey.shared.ui.account

import br.com.tavaressan.cafey.shared.auth.FakeTokenStorage
import br.com.tavaressan.cafey.shared.network.ApiClient
import br.com.tavaressan.cafey.shared.network.AuthApi
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ativação com códigos de recuperação e desativação do 2FA na tela de Conta. */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelMfaTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createAuthApi(handler: (String) -> Pair<HttpStatusCode, String>): AuthApi {
        val tokenStorage = FakeTokenStorage()
        val engine = MockEngine { request ->
            val (status, body) = handler(request.url.encodedPath)
            respond(content = body, status = status, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val httpClient = HttpClient(engine, sharedHttpClientConfig("http://localhost:8080", tokenStorage))
        return AuthApi(ApiClient("http://localhost:8080", tokenStorage, httpClient), tokenStorage)
    }

    private val codigos = (1..10).joinToString(",") { "\"AAAAA-BBBB$it\"" }

    @Test
    fun ativar_mostra_os_codigos_de_recuperacao_uma_vez() = runTest(testDispatcher) {
        val authApi = createAuthApi { path ->
            when (path) {
                "/auth/2fa/status" -> HttpStatusCode.OK to """{"mfaHabilitado":false,"codigosRecuperacaoRestantes":0}"""
                "/auth/2fa/configurar" -> HttpStatusCode.OK to """{"otpauthUri":"otpauth://totp/Cafey:a?secret=ABC","segredo":"ABC"}"""
                "/auth/2fa/ativar" -> HttpStatusCode.OK to """{"mensagem":"2FA ativado com sucesso","codigosRecuperacao":[$codigos]}"""
                else -> HttpStatusCode.NotFound to "{}"
            }
        }
        val viewModel = AccountViewModel(authApi)
        viewModel.uiState.first { it.mfaHabilitado == false }

        viewModel.iniciarConfiguracao()
        viewModel.uiState.first { it.showConfigDialog }
        viewModel.onCodigoAtivacaoChange("123456")
        viewModel.confirmarAtivacao()

        val ativado = viewModel.uiState.first { it.mfaHabilitado == true }
        assertEquals(10, ativado.codigosRecuperacao?.size)
        assertEquals(10, ativado.codigosRecuperacaoRestantes)
        assertFalse(ativado.showConfigDialog)

        viewModel.dispensarCodigosRecuperacao()
        assertNull(viewModel.uiState.value.codigosRecuperacao)
    }

    @Test
    fun desativar_exige_codigo_valido_e_atualiza_o_status() = runTest(testDispatcher) {
        var desativarStatus = HttpStatusCode.Unauthorized
        val authApi = createAuthApi { path ->
            when (path) {
                "/auth/2fa/status" -> HttpStatusCode.OK to """{"mfaHabilitado":true,"codigosRecuperacaoRestantes":7}"""
                "/auth/2fa/desativar" -> desativarStatus to
                    if (desativarStatus == HttpStatusCode.OK) """{"mensagem":"2FA desativado"}""" else """{"status":401,"detail":"Código inválido"}"""
                else -> HttpStatusCode.NotFound to "{}"
            }
        }
        val viewModel = AccountViewModel(authApi)
        assertEquals(7, viewModel.uiState.first { it.mfaHabilitado == true }.codigosRecuperacaoRestantes)

        viewModel.abrirDesativacao()
        viewModel.onCodigoDesativacaoChange("12")
        viewModel.confirmarDesativacao()
        assertFalse(viewModel.uiState.value.mfaDeactivateLoading, "código incompleto não deve chamar o backend")

        viewModel.onCodigoDesativacaoChange("000000")
        viewModel.confirmarDesativacao()
        val erro = viewModel.uiState.first { it.mfaErrorMessage != null }
        assertEquals("Código inválido", erro.mfaErrorMessage)
        assertTrue(erro.mfaHabilitado == true)
        assertTrue(erro.showDesativarDialog)

        desativarStatus = HttpStatusCode.OK
        viewModel.onCodigoDesativacaoChange("abcde-fghjk")
        viewModel.confirmarDesativacao()
        val desligado = viewModel.uiState.first { it.mfaHabilitado == false }
        assertFalse(desligado.showDesativarDialog)
        assertEquals(0, desligado.codigosRecuperacaoRestantes)
    }
}
