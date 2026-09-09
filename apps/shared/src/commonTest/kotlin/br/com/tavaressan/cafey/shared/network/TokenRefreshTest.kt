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

/**
 * Reproduz a rotação de refresh token (BE-05): um `GET` autenticado falha com 401 usando o access
 * token expirado (`old-access`), o plugin `Auth` do Ktor chama `/auth/refresh` automaticamente com
 * o refresh token guardado, recebe um par **novo** e refaz a chamada original — sem o app precisar
 * saber que isso aconteceu.
 */
class TokenRefreshTest {

    @Test
    fun expiredAccessToken_triggersRefresh_andRetriesWithNewToken() = runTest {
        val tokenStorage = FakeTokenStorage(StoredTokens(accessToken = "old-access", refreshToken = "old-refresh"))
        var protectedCallCount = 0

        val engine = MockEngine { request ->
            when {
                request.url.encodedPath == "/auth/refresh" -> {
                    respond(
                        content = """{"accessToken":"new-access","refreshToken":"new-refresh","tokenType":"Bearer","expiresIn":900}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                request.url.encodedPath == "/dispositivos" -> {
                    protectedCallCount++
                    val authHeader = request.headers[HttpHeaders.Authorization]
                    if (authHeader == "Bearer old-access") {
                        respond(content = "", status = HttpStatusCode.Unauthorized)
                    } else {
                        respond(
                            content = "[]",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }
                else -> respond(content = "", status = HttpStatusCode.NotFound)
            }
        }

        val client = HttpClient(engine, sharedHttpClientConfig("https://api.cafey.test", tokenStorage))

        val response = client.get("/dispositivos")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(2, protectedCallCount) // 401 na primeira, 200 depois do refresh
        assertEquals(StoredTokens("new-access", "new-refresh"), tokenStorage.load())
    }
}
