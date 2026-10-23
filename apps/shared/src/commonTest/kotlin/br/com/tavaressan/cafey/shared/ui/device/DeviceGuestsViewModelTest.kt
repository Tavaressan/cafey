package br.com.tavaressan.cafey.shared.ui.device

import br.com.tavaressan.cafey.shared.auth.FakeTokenStorage
import br.com.tavaressan.cafey.shared.auth.StoredTokens
import br.com.tavaressan.cafey.shared.domain.model.PapelDispositivo
import br.com.tavaressan.cafey.shared.domain.validation.FieldError
import br.com.tavaressan.cafey.shared.network.ApiClient
import br.com.tavaressan.cafey.shared.network.DeviceApi
import br.com.tavaressan.cafey.shared.network.sharedHttpClientConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceGuestsViewModelTest {
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
    fun loadGuests_success_populatesGuestsList() = runTest(testDispatcher) {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/dispositivos/dev-10/compartilhamentos", request.url.encodedPath)
            respond(
                content = """
                    [
                        {
                            "usuarioId": "usr-1",
                            "nome": "Convidado Ana",
                            "email": "ana@cafey.com",
                            "papel": "CONVIDADO",
                            "compartilhadoEm": "2026-10-15T10:00:00Z"
                        }
                    ]
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
        val deviceApi = DeviceApi(ApiClient("https://api.cafey.test", tokenStorage, HttpClient(engine, sharedHttpClientConfig("https://api.cafey.test", tokenStorage))))
        val viewModel = DeviceGuestsViewModel("dev-10", deviceApi, coroutineScope = this)

        val state = viewModel.uiState.first { !it.loading }
        assertEquals(1, state.guests.size)
        assertEquals("usr-1", state.guests[0].usuarioId)
        assertEquals("Convidado Ana", state.guests[0].nome)
        assertEquals("ana@cafey.com", state.guests[0].email)
        assertEquals(PapelDispositivo.CONVIDADO, state.guests[0].papel)
        assertNull(state.errorMessage)
    }

    @Test
    fun loadGuests_error_setsErrorMessage() = runTest(testDispatcher) {
        val engine = MockEngine {
            respond(
                content = """{"title":"Erro","status":500,"detail":"Falha interna"}""",
                status = HttpStatusCode.InternalServerError,
                headers = jsonHeaders,
            )
        }
        val deviceApi = DeviceApi(ApiClient("https://api.cafey.test", tokenStorage, HttpClient(engine, sharedHttpClientConfig("https://api.cafey.test", tokenStorage))))
        val viewModel = DeviceGuestsViewModel("dev-10", deviceApi, coroutineScope = this)

        val state = viewModel.uiState.first { !it.loading }
        assertEquals(0, state.guests.size)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("Falha interna"))
    }

    @Test
    fun inviteGuest_invalidEmail_setsValidationErrorWithoutCallingApi() = runTest(testDispatcher) {
        var apiCalled = false
        val engine = MockEngine {
            apiCalled = true
            respond(content = "[]", status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val deviceApi = DeviceApi(ApiClient("https://api.cafey.test", tokenStorage, HttpClient(engine, sharedHttpClientConfig("https://api.cafey.test", tokenStorage))))
        val viewModel = DeviceGuestsViewModel("dev-10", deviceApi, coroutineScope = this)
        viewModel.uiState.first { !it.loading }

        apiCalled = false
        viewModel.onEmailChange("email-invalido")
        viewModel.inviteGuest()

        val state = viewModel.uiState.value
        assertEquals(FieldError.InvalidEmail, state.emailError)
        assertFalse(apiCalled)
    }

    @Test
    fun inviteGuest_success_addsGuestAndClearsEmail() = runTest(testDispatcher) {
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Get) {
                respond(content = "[]", status = HttpStatusCode.OK, headers = jsonHeaders)
            } else {
                assertEquals(HttpMethod.Post, request.method)
                assertEquals("/dispositivos/dev-10/compartilhar", request.url.encodedPath)
                val body = request.body.toByteArray().decodeToString()
                assertTrue(body.contains("novo@cafey.com"))
                respond(
                    content = """
                        {
                            "usuarioId": "usr-novo",
                            "nome": "Novo Amigo",
                            "email": "novo@cafey.com",
                            "papel": "CONVIDADO",
                            "compartilhadoEm": "2026-10-23T12:00:00Z"
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.Created,
                    headers = jsonHeaders,
                )
            }
        }
        val deviceApi = DeviceApi(ApiClient("https://api.cafey.test", tokenStorage, HttpClient(engine, sharedHttpClientConfig("https://api.cafey.test", tokenStorage))))
        val viewModel = DeviceGuestsViewModel("dev-10", deviceApi, coroutineScope = this)
        viewModel.uiState.first { !it.loading }
        assertEquals(0, viewModel.uiState.value.guests.size)

        viewModel.onEmailChange("novo@cafey.com")
        viewModel.inviteGuest()

        val state = viewModel.uiState.first { it.successMessage != null || it.errorMessage != null }
        assertFalse(state.isInviting)
        assertEquals(1, state.guests.size)
        assertEquals("novo@cafey.com", state.guests[0].email)
        assertEquals("", state.emailInput)
        assertNotNull(state.successMessage)
        assertTrue(state.successMessage!!.contains("novo@cafey.com"))
    }

    @Test
    fun inviteGuest_apiError_setsErrorMessage() = runTest(testDispatcher) {
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Get) {
                respond(content = "[]", status = HttpStatusCode.OK, headers = jsonHeaders)
            } else {
                respond(
                    content = """{"title":"Não encontrado","status":404,"detail":"Usuário não cadastrado"}""",
                    status = HttpStatusCode.NotFound,
                    headers = jsonHeaders,
                )
            }
        }
        val deviceApi = DeviceApi(ApiClient("https://api.cafey.test", tokenStorage, HttpClient(engine, sharedHttpClientConfig("https://api.cafey.test", tokenStorage))))
        val viewModel = DeviceGuestsViewModel("dev-10", deviceApi, coroutineScope = this)
        viewModel.uiState.first { !it.loading }

        viewModel.onEmailChange("naoexiste@cafey.com")
        viewModel.inviteGuest()

        val state = viewModel.uiState.first { it.errorMessage != null }
        assertFalse(state.isInviting)
        assertEquals(0, state.guests.size)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("Usuário não cadastrado"))
    }

    @Test
    fun removeGuest_success_removesFromList() = runTest(testDispatcher) {
        var deleteExecuted = false
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Get) {
                respond(
                    content = """
                        [
                            {
                                "usuarioId": "usr-1",
                                "nome": "Convidado Ana",
                                "email": "ana@cafey.com",
                                "papel": "CONVIDADO",
                                "compartilhadoEm": "2026-10-15T10:00:00Z"
                            }
                        ]
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            } else if (request.method == HttpMethod.Delete) {
                assertEquals("/dispositivos/dev-10/compartilhar/usr-1", request.url.encodedPath)
                deleteExecuted = true
                respond(content = "", status = HttpStatusCode.NoContent)
            } else {
                respond(content = "", status = HttpStatusCode.NotFound)
            }
        }
        val deviceApi = DeviceApi(ApiClient("https://api.cafey.test", tokenStorage, HttpClient(engine, sharedHttpClientConfig("https://api.cafey.test", tokenStorage))))
        val viewModel = DeviceGuestsViewModel("dev-10", deviceApi, coroutineScope = this)
        viewModel.uiState.first { !it.loading }
        assertEquals(1, viewModel.uiState.value.guests.size)

        viewModel.removeGuest("usr-1")

        val state = viewModel.uiState.first { it.successMessage != null || it.errorMessage != null }
        assertTrue(deleteExecuted)
        assertFalse(state.isRemoving)
        assertEquals(0, state.guests.size)
        assertNotNull(state.successMessage)
    }

    @Test
    fun removeGuest_apiError_setsErrorMessage() = runTest(testDispatcher) {
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Get) {
                respond(
                    content = """
                        [
                            {
                                "usuarioId": "usr-1",
                                "nome": "Convidado Ana",
                                "email": "ana@cafey.com",
                                "papel": "CONVIDADO",
                                "compartilhadoEm": "2026-10-15T10:00:00Z"
                            }
                        ]
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            } else {
                respond(
                    content = """{"title":"Erro","status":403,"detail":"Sem permissão"}""",
                    status = HttpStatusCode.Forbidden,
                    headers = jsonHeaders,
                )
            }
        }
        val deviceApi = DeviceApi(ApiClient("https://api.cafey.test", tokenStorage, HttpClient(engine, sharedHttpClientConfig("https://api.cafey.test", tokenStorage))))
        val viewModel = DeviceGuestsViewModel("dev-10", deviceApi, coroutineScope = this)
        viewModel.uiState.first { !it.loading }
        assertEquals(1, viewModel.uiState.value.guests.size)

        viewModel.removeGuest("usr-1")

        val state = viewModel.uiState.first { it.errorMessage != null }
        assertFalse(state.isRemoving)
        assertEquals(1, state.guests.size)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("Sem permissão"))
    }

    @Test
    fun clearMessages_clearsMessages() = runTest(testDispatcher) {
        val engine = MockEngine {
            respond(content = "[]", status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        val deviceApi = DeviceApi(ApiClient("https://api.cafey.test", tokenStorage, HttpClient(engine, sharedHttpClientConfig("https://api.cafey.test", tokenStorage))))
        val viewModel = DeviceGuestsViewModel("dev-10", deviceApi, coroutineScope = this)
        viewModel.uiState.first { !it.loading }

        viewModel.onEmailChange("invalid")
        viewModel.inviteGuest()
        assertEquals(FieldError.InvalidEmail, viewModel.uiState.value.emailError)

        viewModel.clearMessages()
        assertNull(viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.successMessage)
    }
}
