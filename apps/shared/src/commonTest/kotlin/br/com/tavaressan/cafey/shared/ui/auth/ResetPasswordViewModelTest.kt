package br.com.tavaressan.cafey.shared.ui.auth

import br.com.tavaressan.cafey.shared.auth.FakeTokenStorage
import br.com.tavaressan.cafey.shared.domain.validation.FieldError
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
class ResetPasswordViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createAuthApi(statusCode: HttpStatusCode, responseBody: String): AuthApi {
        val tokenStorage = FakeTokenStorage()
        val engine = MockEngine { _ ->
            respond(
                content = responseBody,
                status = statusCode,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val httpClient = HttpClient(engine, sharedHttpClientConfig("http://localhost:8080", tokenStorage))
        val apiClient = ApiClient("http://localhost:8080", tokenStorage, httpClient)
        return AuthApi(apiClient, tokenStorage)
    }

    @Test
    fun submit_withBlankFields_setsRequiredErrors() {
        val authApi = createAuthApi(HttpStatusCode.OK, "")
        val viewModel = ResetPasswordViewModel(authApi)

        viewModel.onTokenChange("  ")
        viewModel.onNovaSenhaChange("")
        viewModel.onConfirmacaoSenhaChange("")
        viewModel.submit()

        val errors = viewModel.uiState.value.errors
        assertEquals(FieldError.Required, errors.token)
        assertEquals(FieldError.Required, errors.novaSenha)
        assertEquals(FieldError.Required, errors.confirmacaoSenha)
    }

    @Test
    fun submit_withShortPassword_setsPasswordTooShortError() {
        val authApi = createAuthApi(HttpStatusCode.OK, "")
        val viewModel = ResetPasswordViewModel(authApi)

        viewModel.onTokenChange("valid-token")
        viewModel.onNovaSenhaChange("123")
        viewModel.onConfirmacaoSenhaChange("123")
        viewModel.submit()

        val errors = viewModel.uiState.value.errors
        assertNull(errors.token)
        assertEquals(FieldError.PasswordTooShort, errors.novaSenha)
    }

    @Test
    fun submit_withMismatchedPassword_setsPasswordMismatchError() {
        val authApi = createAuthApi(HttpStatusCode.OK, "")
        val viewModel = ResetPasswordViewModel(authApi)

        viewModel.onTokenChange("valid-token")
        viewModel.onNovaSenhaChange("senha123")
        viewModel.onConfirmacaoSenhaChange("outrasenha")
        viewModel.submit()

        val errors = viewModel.uiState.value.errors
        assertNull(errors.token)
        assertNull(errors.novaSenha)
        assertEquals(FieldError.PasswordMismatch, errors.confirmacaoSenha)
    }

    @Test
    fun submit_withValidData_successFlow() = runTest {
        val authApi = createAuthApi(HttpStatusCode.OK, "")
        val viewModel = ResetPasswordViewModel(authApi)

        viewModel.onTokenChange("valid-token-123")
        viewModel.onNovaSenhaChange("novaSenhaSegura")
        viewModel.onConfirmacaoSenhaChange("novaSenhaSegura")
        viewModel.submit()
        // A chamada HTTP do MockEngine roda fora do agendador do teste; advanceUntilIdle não a espera.
        val state = viewModel.uiState.first { it.success || it.errorMessage != null }
        assertTrue(state.success)
        assertNull(state.errorMessage)
    }

    @Test
    fun submit_withInvalidOrExpiredToken_displaysProblemDetailErrorMessage() = runTest {
        val authApi = createAuthApi(
            HttpStatusCode.BadRequest,
            """{"title":"Bad Request","detail":"Token de recuperação inválido ou expirado"}""",
        )
        val viewModel = ResetPasswordViewModel(authApi)

        viewModel.onTokenChange("expired-token")
        viewModel.onNovaSenhaChange("novaSenhaSegura")
        viewModel.onConfirmacaoSenhaChange("novaSenhaSegura")
        viewModel.submit()
        // A chamada HTTP do MockEngine roda fora do agendador do teste; advanceUntilIdle não a espera.
        val state = viewModel.uiState.first { it.success || it.errorMessage != null }
        assertFalse(state.success)
        assertEquals("Token de recuperação inválido ou expirado", state.errorMessage)
    }
}
