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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ForgotPasswordViewModelTest {

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
    fun submit_withBlankEmail_setsRequiredError() {
        val authApi = createAuthApi(HttpStatusCode.OK, "{}")
        val viewModel = ForgotPasswordViewModel(authApi)

        viewModel.onEmailChange("  ")
        viewModel.submit()

        assertEquals(FieldError.Required, viewModel.uiState.value.emailError)
    }

    @Test
    fun submit_withInvalidEmail_setsInvalidEmailError() {
        val authApi = createAuthApi(HttpStatusCode.OK, "{}")
        val viewModel = ForgotPasswordViewModel(authApi)

        viewModel.onEmailChange("usuario-invalido")
        viewModel.submit()

        assertEquals(FieldError.InvalidEmail, viewModel.uiState.value.emailError)
    }

    @Test
    fun submit_withValidEmail_successFlow() = runTest {
        val authApi = createAuthApi(
            HttpStatusCode.OK,
            """{"mensagem":"Instruções de recuperação enviadas com sucesso."}""",
        )
        val viewModel = ForgotPasswordViewModel(authApi)

        viewModel.onEmailChange("usuario@example.com")
        viewModel.submit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.emailError)
        assertNull(state.errorMessage)
        assertEquals("Instruções de recuperação enviadas com sucesso.", state.successMessage)
    }

    @Test
    fun submit_withApiError_displaysErrorMessage() = runTest {
        val authApi = createAuthApi(
            HttpStatusCode.BadRequest,
            """{"title":"Bad Request","detail":"Muitas tentativas. Aguarde 1 minuto."}""",
        )
        val viewModel = ForgotPasswordViewModel(authApi)

        viewModel.onEmailChange("usuario@example.com")
        viewModel.submit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.successMessage)
        assertEquals("Muitas tentativas. Aguarde 1 minuto.", state.errorMessage)
    }
}
