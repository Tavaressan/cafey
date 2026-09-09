package br.com.tavaressan.cafey.shared.network

import br.com.tavaressan.cafey.shared.auth.StoredTokens
import br.com.tavaressan.cafey.shared.auth.TokenStorage
import br.com.tavaressan.cafey.shared.domain.model.AuthResponse
import br.com.tavaressan.cafey.shared.domain.model.RefreshRequest
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Configuração de plugins compartilhada por todo `HttpClient` da app — extraída à parte para que
 * os testes (`commonTest`) montem o mesmo comportamento sobre `MockEngine` em vez do engine real
 * de cada plataforma.
 *
 * Lida com a rotação de refresh token do backend (BE-05): a cada renovação bem-sucedida, tanto o
 * access quanto o **novo** refresh token são persistidos — reusar o refresh token antigo (replay)
 * é detectado pelo backend e derruba a família inteira, daí `refreshTokens` limpar o armazenamento
 * local quando o refresh falha.
 */
fun sharedHttpClientConfig(baseUrl: String, tokenStorage: TokenStorage): HttpClientConfig<*>.() -> Unit = {
    expectSuccess = true
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(Logging) {
        level = LogLevel.INFO
    }
    install(HttpRequestRetry) {
        retryOnServerErrors(maxRetries = 2)
        exponentialDelay()
    }
    install(Auth) {
        bearer {
            loadTokens {
                tokenStorage.load()?.let { BearerTokens(it.accessToken, it.refreshToken) }
            }
            refreshTokens {
                val stored = tokenStorage.load() ?: return@refreshTokens null
                try {
                    val response: AuthResponse = client.post {
                        url.takeFrom("$baseUrl/auth/refresh")
                        markAsRefreshTokenRequest()
                        contentType(ContentType.Application.Json)
                        setBody(RefreshRequest(stored.refreshToken))
                    }.body()
                    tokenStorage.save(StoredTokens(response.accessToken, response.refreshToken))
                    BearerTokens(response.accessToken, response.refreshToken)
                } catch (e: Exception) {
                    // Refresh token inválido, expirado ou reuso detectado (BE-05): a sessão
                    // acabou, não há como continuar autenticado sem novo login.
                    tokenStorage.clear()
                    null
                }
            }
        }
    }
    defaultRequest {
        url(baseUrl)
    }
}

/** Fábrica do [HttpClient] compartilhado por todos os `*Api` (spec §2.3: um único cliente HTTP,
 * configurado uma vez em `shared`, nunca reimplementado por plataforma). */
class ApiClient(
    baseUrl: String,
    private val tokenStorage: TokenStorage,
) {
    val http: HttpClient = createPlatformHttpClient(sharedHttpClientConfig(baseUrl, tokenStorage))

    suspend fun isAuthenticated(): Boolean = tokenStorage.load() != null

    suspend fun logout() = tokenStorage.clear()
}
