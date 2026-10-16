package br.com.tavaressan.cafey.shared.network

import br.com.tavaressan.cafey.shared.auth.FakeTokenStorage
import br.com.tavaressan.cafey.shared.auth.StoredTokens
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Issue #183 / #198 — `ApiClient.logout()` e `AuthApi.logout()` devem limpar o armazenamento local
 * e invalidar o cache em memória do plugin BearerAuthProvider do Ktor para prevenir vazamento de
 * sessão em dispositivos compartilhados.
 */
class ApiClientLogoutTest {

    @Test
    fun logoutClearsStoredTokens() = runTest {
        val tokenStorage = FakeTokenStorage(StoredTokens(accessToken = "access", refreshToken = "refresh"))
        val apiClient = ApiClient(baseUrl = "https://api.cafey.test", tokenStorage = tokenStorage)

        apiClient.logout()

        assertNull(tokenStorage.load())
    }

    @Test
    fun logoutClearsKtorBearerAuthCache_preventingSessionLeakOnMultiUserDevice() = runTest {
        val tokenStorage = FakeTokenStorage(StoredTokens(accessToken = "user-a-access", refreshToken = "user-a-refresh"))
        val receivedAuthHeaders = mutableListOf<String?>()

        val engine = MockEngine { request ->
            receivedAuthHeaders.add(request.headers[HttpHeaders.Authorization])
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val httpClient = HttpClient(engine, sharedHttpClientConfig("https://api.cafey.test", tokenStorage))
        val apiClient = ApiClient(baseUrl = "https://api.cafey.test", tokenStorage = tokenStorage, httpClient = httpClient)

        // Usuário A faz requisição autenticada: token A é enviado e cacheado em memória pelo BearerAuthProvider
        apiClient.http.get("/test")
        assertEquals("Bearer user-a-access", receivedAuthHeaders.last())

        // Usuário A desloga
        apiClient.logout()
        assertNull(tokenStorage.load())

        // Usuário B faz login no mesmo dispositivo / mesmo processo
        tokenStorage.save(StoredTokens(accessToken = "user-b-access", refreshToken = "user-b-refresh"))

        // Usuário B faz requisição autenticada
        apiClient.http.get("/test")

        // O cabeçalho deve conter o token do Usuário B, não o token do Usuário A que estaria em cache
        assertEquals("Bearer user-b-access", receivedAuthHeaders.last())
    }

    @Test
    fun authApiLogoutDelegatesToApiClientLogout() = runTest {
        val tokenStorage = FakeTokenStorage(StoredTokens(accessToken = "user-a-access", refreshToken = "user-a-refresh"))
        val receivedAuthHeaders = mutableListOf<String?>()

        val engine = MockEngine { request ->
            receivedAuthHeaders.add(request.headers[HttpHeaders.Authorization])
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val httpClient = HttpClient(engine, sharedHttpClientConfig("https://api.cafey.test", tokenStorage))
        val apiClient = ApiClient(baseUrl = "https://api.cafey.test", tokenStorage = tokenStorage, httpClient = httpClient)
        val authApi = AuthApi(apiClient = apiClient, tokenStorage = tokenStorage)

        apiClient.http.get("/test")
        assertEquals("Bearer user-a-access", receivedAuthHeaders.last())

        authApi.logout()
        assertNull(tokenStorage.load())

        // Login do Usuário B
        tokenStorage.save(StoredTokens(accessToken = "user-b-access", refreshToken = "user-b-refresh"))
        apiClient.http.get("/test")
        assertEquals("Bearer user-b-access", receivedAuthHeaders.last())
    }
}
