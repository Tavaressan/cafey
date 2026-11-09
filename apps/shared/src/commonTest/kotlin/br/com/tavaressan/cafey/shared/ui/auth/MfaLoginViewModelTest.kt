package br.com.tavaressan.cafey.shared.ui.auth

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

@OptIn(ExperimentalCoroutinesApi::class)
class MfaLoginViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createAuthApi(handler: suspend (String) -> Pair<HttpStatusCode, String>): AuthApi {
        val tokenStorage = FakeTokenStorage()
        val engine = MockEngine { request ->
            val (status, body) = handler(request.url.encodedPath)
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val httpClient = HttpClient(engine, sharedHttpClientConfig("http://localhost:8080", tokenStorage))
        val apiClient = ApiClient("http://localhost:8080", tokenStorage, httpClient)
        return AuthApi(apiClient, tokenStorage)
    }

    @Test
    fun login_sem_2fa_sinaliza_sucesso() = runTest {
        val authApi = createAuthApi { path ->
            if (path == "/auth/login") {
                HttpStatusCode.OK to """{"accessToken":"a1","refreshToken":"r1","tokenType":"Bearer","expiresIn":900}"""
            } else {
                HttpStatusCode.NotFound to """{"detail":"nao encontrado"}"""
            }
        }
        val viewModel = LoginViewModel(authApi)
        viewModel.onEmailChange("user@cafey.com")
        viewModel.onSenhaChange("senha123")

        viewModel.submit()

        val state = viewModel.uiState.first { it.success || it.errorMessage != null }
        assertTrue(state.success)
        assertNull(state.mfaChallengeToken)
        assertNull(state.errorMessage)
    }

    @Test
    fun login_com_2fa_retorna_desafio_sem_sucesso() = runTest {
        val authApi = createAuthApi { path ->
            if (path == "/auth/login") {
                HttpStatusCode.OK to """{"mfaRequerido":true,"desafioToken":"desafio.jwt","tokenType":"Bearer","expiresIn":300}"""
            } else {
                HttpStatusCode.NotFound to """{"detail":"nao encontrado"}"""
            }
        }
        val viewModel = LoginViewModel(authApi)
        viewModel.onEmailChange("user@cafey.com")
        viewModel.onSenhaChange("senha123")

        viewModel.submit()

        val state = viewModel.uiState.first { it.mfaChallengeToken != null || it.errorMessage != null }
        assertFalse(state.success)
        assertEquals("desafio.jwt", state.mfaChallengeToken)
        assertNull(state.errorMessage)
    }

    @Test
    fun login_com_2fa_indisponivel_mostra_mensagem_amigavel() = runTest {
        val authApi = createAuthApi { path ->
            if (path == "/auth/login") {
                HttpStatusCode.ServiceUnavailable to """{"title":"Service Unavailable","detail":"2FA indisponível no servidor"}"""
            } else {
                HttpStatusCode.NotFound to """{"detail":"nao encontrado"}"""
            }
        }
        val viewModel = LoginViewModel(authApi)
        viewModel.onEmailChange("user@cafey.com")
        viewModel.onSenhaChange("senha123")

        viewModel.submit()

        val state = viewModel.uiState.first { it.errorMessage != null }
        assertEquals("2FA indisponível no servidor", state.errorMessage)
        assertFalse(state.success)
    }

    @Test
    fun validacao_de_codigo_aceita_apenas_6_digitos_numericos() = runTest {
        // isCodigoValido é puro: cobre a regra de 6 dígitos numéricos.
        assertTrue(MfaChallengeViewModel.isCodigoValido("123456"))
        assertFalse(MfaChallengeViewModel.isCodigoValido("12345"))
        assertFalse(MfaChallengeViewModel.isCodigoValido("1234567"))
        assertFalse(MfaChallengeViewModel.isCodigoValido("abcdef"))
        assertFalse(MfaChallengeViewModel.isCodigoValido("12a456"))

        // onCodigoChange filtra não dígitos e limita a 6.
        val authApi = createAuthApi { path ->
            if (path == "/auth/2fa/validar") {
                HttpStatusCode.OK to """{"accessToken":"a","refreshToken":"r","tokenType":"Bearer","expiresIn":900}"""
            } else {
                HttpStatusCode.NotFound to """{}"""
            }
        }
        val viewModel = MfaChallengeViewModel(authApi, "desafio.jwt")

        viewModel.onCodigoChange("123")
        assertEquals("123", viewModel.uiState.value.codigo)

        viewModel.onCodigoChange("12ab34")
        assertEquals("1234", viewModel.uiState.value.codigo)

        viewModel.onCodigoChange("123456789")
        // truncado a 6 e auto-submetido; o código no estado fica "123456"
        assertEquals("123456", viewModel.uiState.value.codigo)
    }

    @Test
    fun desafio_mfa_valida_codigo_e_sinaliza_sucesso() = runTest {
        val authApi = createAuthApi { path ->
            if (path == "/auth/2fa/validar") {
                HttpStatusCode.OK to """{"accessToken":"a2","refreshToken":"r2","tokenType":"Bearer","expiresIn":900}"""
            } else {
                HttpStatusCode.NotFound to """{"detail":"nao encontrado"}"""
            }
        }
        val viewModel = MfaChallengeViewModel(authApi, "desafio.jwt")

        viewModel.onCodigoChange("123456")
        val state = viewModel.uiState.first { it.success || it.errorMessage != null }
        assertTrue(state.success)
        assertNull(state.errorMessage)
    }
}
