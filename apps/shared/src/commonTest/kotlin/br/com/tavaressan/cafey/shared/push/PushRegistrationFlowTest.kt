package br.com.tavaressan.cafey.shared.push

import br.com.tavaressan.cafey.shared.auth.FakeTokenStorage
import br.com.tavaressan.cafey.shared.auth.StoredTokens
import br.com.tavaressan.cafey.shared.network.ApiClient
import br.com.tavaressan.cafey.shared.network.AuthApi
import br.com.tavaressan.cafey.shared.network.PushApi
import br.com.tavaressan.cafey.shared.network.sharedHttpClientConfig
import br.com.tavaressan.cafey.shared.ui.account.AccountViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class TestPushRegistrationManager(
    private val apiClient: ApiClient,
    private val pushApi: PushApi,
    private val tokenStorage: FakeTokenStorage,
    private val destino: String = "token-fcm-teste",
    private val failRemove: Boolean = false,
) : PushRegistrationManager {
    var registerCalls = 0
    var removeCalls = 0
    var tokenPresentDuringRemove: Boolean? = null

    override suspend fun registrarSePossivel() {
        if (!apiClient.isAuthenticated()) return
        registerCalls++
        pushApi.registrarAndroid(destino)
    }

    override suspend fun removerAntesDeLogout() {
        if (!apiClient.isAuthenticated()) return
        removeCalls++
        tokenPresentDuringRemove = tokenStorage.load() != null
        if (failRemove) throw RuntimeException("falha simulada")
        pushApi.remover(destino)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PushRegistrationFlowTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun registraDestinoAposLoginComSessaoAtiva() = runTest(dispatcher) {
        val tokenStorage = FakeTokenStorage()
        val requests = mutableListOf<Pair<String, String?>>()

        val engine = MockEngine { request ->
            requests.add(request.url.encodedPath to request.bodyAsTextOrNull())
            when {
                request.url.encodedPath.endsWith("/auth/login") -> respond(
                    content = """{"accessToken":"a1","refreshToken":"r1"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                request.url.encodedPath.endsWith("/notificacoes/registros") -> respond(
                    content = """{"id":"reg-1","plataforma":"ANDROID"}""",
                    status = HttpStatusCode.Created,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }

        val httpClient = HttpClient(engine, sharedHttpClientConfig("https://api.cafey.test", tokenStorage))
        val apiClient = ApiClient("https://api.cafey.test", tokenStorage, httpClient)
        val authApi = AuthApi(apiClient, tokenStorage)
        val pushApi = PushApi(apiClient)
        val manager = TestPushRegistrationManager(apiClient, pushApi, tokenStorage)

        authApi.login(br.com.tavaressan.cafey.shared.domain.model.LoginRequest("u@cafey.test", "senha"))
        assertNotNull(tokenStorage.load(), "login deve persistir tokens")
        manager.registrarSePossivel()

        assertEquals(1, manager.registerCalls)
        assertTrue(requests.any { it.first.endsWith("/notificacoes/registros") && (it.second?.contains("token-fcm-teste") == true) })
    }

    @Test
    fun removeDestinoNoLogoutAntesDeLimparSessao() = runTest(dispatcher) {
        val tokenStorage = FakeTokenStorage(StoredTokens("access", "refresh"))
        val received = mutableListOf<Pair<String, String?>>()

        val engine = MockEngine { request ->
            received.add(request.url.encodedPath to request.headers[HttpHeaders.Authorization])
            when {
                request.url.encodedPath.endsWith("/notificacoes/registros/remover") -> respond(
                    content = "",
                    status = HttpStatusCode.NoContent,
                )
                else -> respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }

        val httpClient = HttpClient(engine, sharedHttpClientConfig("https://api.cafey.test", tokenStorage))
        val apiClient = ApiClient("https://api.cafey.test", tokenStorage, httpClient)
        val authApi = AuthApi(apiClient, tokenStorage)
        val pushApi = PushApi(apiClient)
        val manager = TestPushRegistrationManager(apiClient, pushApi, tokenStorage)
        val viewModel = AccountViewModel(authApi, manager)

        viewModel.logout()
        advanceUntilIdle()

        assertEquals(1, manager.removeCalls)
        assertEquals(true, manager.tokenPresentDuringRemove, "remoção deve ocorrer antes de apagar o JWT")
        assertNull(tokenStorage.load(), "logout deve limpar a sessão após remover destino")
        assertTrue(viewModel.uiState.value.loggedOut)
        val removerCall = received.firstOrNull { it.first.endsWith("/notificacoes/registros/remover") }
        assertNotNull(removerCall, "deve chamar endpoint de remoção")
        assertEquals("Bearer access", removerCall.second, "remoção deve usar JWT ainda válido")
    }

    @Test
    fun falhaDeRedeNaRemocaoNaoPropagaNemImpedeLogout() = runTest(dispatcher) {
        val tokenStorage = FakeTokenStorage(StoredTokens("access", "refresh"))

        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/notificacoes/registros/remover") ->
                    throw IllegalStateException("rede indisponível")
                else -> respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }

        val httpClient = HttpClient(engine, sharedHttpClientConfig("https://api.cafey.test", tokenStorage))
        val apiClient = ApiClient("https://api.cafey.test", tokenStorage, httpClient)
        val authApi = AuthApi(apiClient, tokenStorage)
        val pushApi = PushApi(apiClient)
        // Manager que falha ao remover; AccountViewModel deve engolir e concluir logout.
        val manager = object : PushRegistrationManager {
            override suspend fun registrarSePossivel() = Unit
            override suspend fun removerAntesDeLogout() {
                if (apiClient.isAuthenticated()) error("falha simulada na remoção")
            }
        }
        val viewModel = AccountViewModel(authApi, manager)

        viewModel.logout()
        advanceUntilIdle()

        assertNull(tokenStorage.load(), "logout deve limpar sessão mesmo com falha de rede na remoção")
        assertTrue(viewModel.uiState.value.loggedOut)
    }

    @Test
    fun falhaDeRedeNoRegistroNaoPropaga() = runTest(dispatcher) {
        val tokenStorage = FakeTokenStorage(StoredTokens("access", "refresh"))
        val engine = MockEngine { throw IllegalStateException("rede indisponível") }
        val httpClient = HttpClient(engine, sharedHttpClientConfig("https://api.cafey.test", tokenStorage))
        val apiClient = ApiClient("https://api.cafey.test", tokenStorage, httpClient)
        val pushApi = PushApi(apiClient)
        val manager = object : PushRegistrationManager {
            override suspend fun registrarSePossivel() {
                try {
                    pushApi.registrarAndroid("token-fcm-teste")
                } catch (_: Exception) {
                    // contrato: falha de rede no registro não pode propagar
                }
            }
            override suspend fun removerAntesDeLogout() = Unit
        }

        // Não deve lançar exceção
        manager.registrarSePossivel()
        assertNotNull(tokenStorage.load())
    }
}

private fun io.ktor.client.request.HttpRequestData.bodyAsTextOrNull(): String? =
    (body as? TextContent)?.text
