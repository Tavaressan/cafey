package br.com.tavaressan.cafey.shared.network

import br.com.tavaressan.cafey.shared.auth.FakeTokenStorage
import br.com.tavaressan.cafey.shared.auth.StoredTokens
import br.com.tavaressan.cafey.shared.domain.model.CompartilharDispositivoRequest
import br.com.tavaressan.cafey.shared.domain.model.PapelDispositivo
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DeviceApiTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val tokenStorage = FakeTokenStorage(StoredTokens("access-token", "refresh-token"))

    @Test
    fun listarCompartilhamentos_sucesso_retornaListaDeConvidados() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/dispositivos/dev-1/compartilhamentos", request.url.encodedPath)
            respond(
                content = """
                    [
                        {
                            "usuarioId": "user-g1",
                            "nome": "Convidado 1",
                            "email": "convidado1@cafey.com",
                            "papel": "CONVIDADO",
                            "compartilhadoEm": "2026-10-15T21:00:00Z"
                        }
                    ]
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
        val httpClient = HttpClient(engine, sharedHttpClientConfig("https://api.cafey.test", tokenStorage))
        val api = DeviceApi(ApiClient("https://api.cafey.test", tokenStorage, httpClient))

        val guests = api.listarCompartilhamentos("dev-1")
        assertEquals(1, guests.size)
        assertEquals("user-g1", guests.first().usuarioId)
        assertEquals("Convidado 1", guests.first().nome)
        assertEquals("convidado1@cafey.com", guests.first().email)
        assertEquals(PapelDispositivo.CONVIDADO, guests.first().papel)
    }

    @Test
    fun listarCompartilhamentos_erro_lancaApiError() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"title":"Não encontrado","status":404,"detail":"Dispositivo não encontrado"}""",
                status = HttpStatusCode.NotFound,
                headers = jsonHeaders,
            )
        }
        val httpClient = HttpClient(engine, sharedHttpClientConfig("https://api.cafey.test", tokenStorage))
        val api = DeviceApi(ApiClient("https://api.cafey.test", tokenStorage, httpClient))

        val error = assertFailsWith<ApiError> {
            api.listarCompartilhamentos("dev-inexistente")
        }
        assertTrue(error is ApiError.Http)
        assertEquals(404, error.status)
        assertTrue(error.message!!.contains("Dispositivo não encontrado"))
    }

    @Test
    fun compartilhar_sucesso_enviaRequestERetornaNovoConvidado() = runTest {
        var requestBody = ""
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/dispositivos/dev-1/compartilhar", request.url.encodedPath)
            requestBody = request.body.toByteArray().decodeToString()
            respond(
                content = """
                    {
                        "usuarioId": "user-g2",
                        "nome": "Convidado 2",
                        "email": "convidado2@cafey.com",
                        "papel": "CONVIDADO",
                        "compartilhadoEm": "2026-10-23T10:00:00Z"
                    }
                """.trimIndent(),
                status = HttpStatusCode.Created,
                headers = jsonHeaders,
            )
        }
        val httpClient = HttpClient(engine, sharedHttpClientConfig("https://api.cafey.test", tokenStorage))
        val api = DeviceApi(ApiClient("https://api.cafey.test", tokenStorage, httpClient))

        val response = api.compartilhar("dev-1", CompartilharDispositivoRequest("convidado2@cafey.com"))
        assertTrue(requestBody.contains(""""email":"convidado2@cafey.com""""))
        assertEquals("user-g2", response.usuarioId)
        assertEquals("convidado2@cafey.com", response.email)
    }

    @Test
    fun compartilhar_erro_lancaApiError() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"title":"Usuário não encontrado","status":404,"detail":"Nenhum usuário com este email"}""",
                status = HttpStatusCode.NotFound,
                headers = jsonHeaders,
            )
        }
        val httpClient = HttpClient(engine, sharedHttpClientConfig("https://api.cafey.test", tokenStorage))
        val api = DeviceApi(ApiClient("https://api.cafey.test", tokenStorage, httpClient))

        val error = assertFailsWith<ApiError> {
            api.compartilhar("dev-1", CompartilharDispositivoRequest("inexistente@cafey.com"))
        }
        assertTrue(error is ApiError.Http)
        assertEquals(404, error.status)
        assertTrue(error.message!!.contains("Nenhum usuário"))
    }

    @Test
    fun removerCompartilhamento_sucesso_executaDelete() = runTest {
        var requestedMethod: HttpMethod? = null
        var requestedPath: String? = null
        val engine = MockEngine { request ->
            requestedMethod = request.method
            requestedPath = request.url.encodedPath
            respond(content = "", status = HttpStatusCode.NoContent)
        }
        val httpClient = HttpClient(engine, sharedHttpClientConfig("https://api.cafey.test", tokenStorage))
        val api = DeviceApi(ApiClient("https://api.cafey.test", tokenStorage, httpClient))

        api.removerCompartilhamento("dev-1", "user-g1")
        assertEquals(HttpMethod.Delete, requestedMethod)
        assertEquals("/dispositivos/dev-1/compartilhar/user-g1", requestedPath)
    }

    @Test
    fun removerCompartilhamento_erro_lancaApiError() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"title":"Não encontrado","status":404,"detail":"Vínculo não encontrado"}""",
                status = HttpStatusCode.NotFound,
                headers = jsonHeaders,
            )
        }
        val httpClient = HttpClient(engine, sharedHttpClientConfig("https://api.cafey.test", tokenStorage))
        val api = DeviceApi(ApiClient("https://api.cafey.test", tokenStorage, httpClient))

        val error = assertFailsWith<ApiError> {
            api.removerCompartilhamento("dev-1", "user-inexistente")
        }
        assertTrue(error is ApiError.Http)
        assertEquals(404, error.status)
    }
}
