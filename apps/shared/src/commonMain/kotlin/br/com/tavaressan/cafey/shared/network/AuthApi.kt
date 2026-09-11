package br.com.tavaressan.cafey.shared.network

import br.com.tavaressan.cafey.shared.auth.StoredTokens
import br.com.tavaressan.cafey.shared.auth.TokenStorage
import br.com.tavaressan.cafey.shared.domain.model.AuthResponse
import br.com.tavaressan.cafey.shared.domain.model.LoginRequest
import br.com.tavaressan.cafey.shared.domain.model.RecuperacaoSenhaResponse
import br.com.tavaressan.cafey.shared.domain.model.RedefinirSenhaRequest
import br.com.tavaressan.cafey.shared.domain.model.RegisterRequest
import br.com.tavaressan.cafey.shared.domain.model.SolicitarRecuperacaoSenhaRequest
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.path

/** Endpoints em `/auth` — espelha `br.com.tavaressan.cafey.auth.AuthController`. */
class AuthApi(
    private val apiClient: ApiClient,
    private val tokenStorage: TokenStorage,
) {
    suspend fun registrar(request: RegisterRequest): AuthResponse =
        apiClient.http.apiRequest<AuthResponse> {
            method = HttpMethod.Post
            url { path("auth", "registrar") }
            contentType(ContentType.Application.Json)
            setBody(request)
        }.also { persistTokens(it) }

    suspend fun login(request: LoginRequest): AuthResponse =
        apiClient.http.apiRequest<AuthResponse> {
            method = HttpMethod.Post
            url { path("auth", "login") }
            contentType(ContentType.Application.Json)
            setBody(request)
        }.also { persistTokens(it) }

    suspend fun solicitarRecuperacaoSenha(email: String): RecuperacaoSenhaResponse =
        apiClient.http.apiRequest {
            method = HttpMethod.Post
            url { path("auth", "recuperar-senha") }
            contentType(ContentType.Application.Json)
            setBody(SolicitarRecuperacaoSenhaRequest(email))
        }

    suspend fun redefinirSenha(token: String, novaSenha: String) {
        apiClient.http.apiRequest<Unit> {
            method = HttpMethod.Post
            url { path("auth", "redefinir-senha") }
            contentType(ContentType.Application.Json)
            setBody(RedefinirSenhaRequest(token, novaSenha))
        }
    }

    suspend fun logout() = tokenStorage.clear()

    /** Login do admin fixo de teste — sem round-trip HTTP, ver [DebugAdminCredentials]. */
    suspend fun loginComoAdminDebug(): AuthResponse =
        AuthResponse(accessToken = "debug-admin-access-token", refreshToken = "debug-admin-refresh-token")
            .also { persistTokens(it) }

    private suspend fun persistTokens(response: AuthResponse) {
        tokenStorage.save(StoredTokens(response.accessToken, response.refreshToken))
    }
}
