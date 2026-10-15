package br.com.tavaressan.cafey.config

import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Valida a configuração de cabeçalhos de proxy reverso (X-Forwarded-Proto, X-Forwarded-Host,
 * X-Forwarded-For e X-Forwarded-Port) sob o Caddy (issue #230).
 *
 * Em produção, o Caddy termina o TLS e encaminha a requisição HTTP localmente. Com a estratégia
 * `server.forward-headers-strategy: native`, o Tomcat ativa o `RemoteIpValve`, que confia nas
 * conexões de proxies internos e reescreve esquema, host, porta e endereço remoto do cliente.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ForwardedHeadersIntegrationTest.ForwardedHeadersProbeConfig::class)
class ForwardedHeadersIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    private val httpClient: HttpClient = HttpClient.newHttpClient()
    private val objectMapper: ObjectMapper = ObjectMapper()

    @TestConfiguration
    class ForwardedHeadersProbeConfig {
        @Bean
        fun forwardedHeadersProbeController() = ForwardedHeadersProbeController()
    }

    @RestController
    class ForwardedHeadersProbeController {
        @GetMapping("/test/request-info")
        fun requestInfo(request: HttpServletRequest): Map<String, Any> {
            return mapOf(
                "scheme" to request.scheme,
                "serverName" to request.serverName,
                "serverPort" to request.serverPort,
                "remoteAddr" to request.remoteAddr,
                "isSecure" to request.isSecure
            )
        }
    }

    @Test
    fun `com cabecalhos X-Forwarded Spring resolve esquema HTTPS host e IP remoto do cliente`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port/test/request-info"))
            .header("X-Forwarded-Proto", "https")
            .header("X-Forwarded-Host", "cafey-backend.duckdns.org")
            .header("X-Forwarded-For", "203.0.113.195")
            .header("X-Forwarded-Port", "443")
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, response.statusCode())

        val json = objectMapper.readTree(response.body())
        assertEquals("https", json.get("scheme").asString())
        assertEquals("cafey-backend.duckdns.org", json.get("serverName").asString())
        assertEquals(443, json.get("serverPort").asInt())
        assertEquals("203.0.113.195", json.get("remoteAddr").asString())
        assertTrue(json.get("isSecure").asBoolean())
    }

    @Test
    fun `com cabecalhos X-Forwarded OpenAPI v3 api-docs declara servers com HTTPS e dominio publico`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port/v3/api-docs"))
            .header("X-Forwarded-Proto", "https")
            .header("X-Forwarded-Host", "cafey-backend.duckdns.org")
            .header("X-Forwarded-For", "203.0.113.195")
            .header("X-Forwarded-Port", "443")
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, response.statusCode())

        val rootNode = objectMapper.readTree(response.body())
        val serversNode = rootNode.get("servers")
        val serverUrl = serversNode.get(0).get("url").asString()
        assertEquals("https://cafey-backend.duckdns.org", serverUrl)
    }

    @Test
    fun `sem cabecalhos X-Forwarded preserva esquema HTTP e host local`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port/test/request-info"))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, response.statusCode())

        val json = objectMapper.readTree(response.body())
        assertEquals("http", json.get("scheme").asString())
        assertEquals("localhost", json.get("serverName").asString())
        assertFalse(json.get("isSecure").asBoolean())
    }
}
