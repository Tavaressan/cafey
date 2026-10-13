package br.com.tavaressan.cafey.security

import br.com.tavaressan.cafey.config.RateLimitProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import tools.jackson.databind.ObjectMapper

class RateLimitingFilterTest {

    private lateinit var objectMapper: ObjectMapper
    private lateinit var properties: RateLimitProperties

    @BeforeEach
    fun setUp() {
        objectMapper = ObjectMapper()
        properties = RateLimitProperties(
            enabled = true,
            loginCapacity = 3,
            loginPeriodSeconds = 60,
            registrarCapacity = 10,
            registrarPeriodSeconds = 60,
            recuperarSenhaCapacity = 5,
            recuperarSenhaPeriodSeconds = 60
        )
    }

    private fun performRequest(
        filter: RateLimitingFilter,
        path: String,
        ip: String = "127.0.0.1"
    ): Pair<MockHttpServletResponse, MockFilterChain> {
        val request = MockHttpServletRequest("POST", path)
        request.remoteAddr = ip
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()
        filter.doFilter(request, response, chain)
        return response to chain
    }

    @Test
    fun `deve permitir requisicoes dentro da cota`() {
        val filter = RateLimitingFilter(properties, objectMapper)

        repeat(3) {
            val (response, chain) = performRequest(filter, "/auth/login")
            assertNotNull(chain.request)
            assertEquals(HttpStatus.OK.value(), response.status)
        }
    }

    @Test
    fun `deve bloquear com 429 e Retry-After apos estourar a cota`() {
        val filter = RateLimitingFilter(properties, objectMapper)
        repeat(3) { performRequest(filter, "/auth/login") }

        val (response, chain) = performRequest(filter, "/auth/login")

        assertNull(chain.request)
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), response.status)
        val retryAfter = response.getHeader("Retry-After")
        assertNotNull(retryAfter)
        assertTrue(retryAfter!!.toInt() > 0)
        assertEquals("application/problem+json", response.contentType?.substringBefore(";"))
        assertTrue(response.contentAsString.contains("\"status\":429"))
    }

    @Test
    fun `nao deve compartilhar cota entre IPs diferentes`() {
        val filter = RateLimitingFilter(properties, objectMapper)
        repeat(3) { performRequest(filter, "/auth/login", ip = "10.0.0.1") }

        val (response, chain) = performRequest(filter, "/auth/login", ip = "10.0.0.2")

        assertNotNull(chain.request)
        assertEquals(HttpStatus.OK.value(), response.status)
    }

    @Test
    fun `nao deve limitar rotas fora do escopo do filtro`() {
        val filter = RateLimitingFilter(properties, objectMapper)

        repeat(10) {
            val (response, chain) = performRequest(filter, "/auth/refresh")
            assertNotNull(chain.request)
            assertEquals(HttpStatus.OK.value(), response.status)
        }
    }

    @Test
    fun `mapa de buckets nao deve crescer sem limite com muitos IPs distintos`() {
        val filter = RateLimitingFilter(properties, objectMapper)

        repeat(filter.maxBuckets + 2_000) { index ->
            performRequest(filter, "/auth/login", ip = "ip-$index")
        }

        assertTrue(filter.bucketCount() <= filter.maxBuckets)
    }

    @Test
    fun `signin e limitado com os mesmos limites do login`() {
        val filter = RateLimitingFilter(properties, objectMapper)
        repeat(3) { performRequest(filter, "/auth/signin") }

        val (response, chain) = performRequest(filter, "/auth/signin")

        assertNull(chain.request)
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), response.status)
    }

    @Test
    fun `login e signin dividem o balde com os limites padrao e a 6a tentativa recebe 429`() {
        val filter = RateLimitingFilter(RateLimitProperties(), objectMapper)
        val rotas = listOf("/auth/login", "/auth/signin")

        repeat(5) { index ->
            val (response, chain) = performRequest(filter, rotas[index % 2])
            assertNotNull(chain.request)
            assertEquals(HttpStatus.OK.value(), response.status)
        }
        val (response, chain) = performRequest(filter, rotas[5 % 2])

        assertNull(chain.request)
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), response.status)
    }

    @Test
    fun `signup e registrar dividem o balde com os limites padrao e a 11a tentativa recebe 429`() {
        val filter = RateLimitingFilter(RateLimitProperties(), objectMapper)
        val rotas = listOf("/auth/registrar", "/auth/signup")

        repeat(10) { index ->
            val (response, chain) = performRequest(filter, rotas[index % 2])
            assertNotNull(chain.request)
            assertEquals(HttpStatus.OK.value(), response.status)
        }
        val (response, chain) = performRequest(filter, rotas[10 % 2])

        assertNull(chain.request)
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), response.status)
    }

    @Test
    fun `signin e signup nao dividem balde entre si`() {
        val filter = RateLimitingFilter(properties, objectMapper)
        repeat(3) { performRequest(filter, "/auth/signin") }

        val (response, chain) = performRequest(filter, "/auth/signup")

        assertNotNull(chain.request)
        assertEquals(HttpStatus.OK.value(), response.status)
    }

    @Test
    fun `esgotar o balde do signin de um IP nao limita outro IP`() {
        val filter = RateLimitingFilter(properties, objectMapper)
        repeat(3) { performRequest(filter, "/auth/signin", ip = "10.0.0.1") }

        val (response, chain) = performRequest(filter, "/auth/login", ip = "10.0.0.2")

        assertNotNull(chain.request)
        assertEquals(HttpStatus.OK.value(), response.status)
    }

    @Test
    fun `deve ignorar rate limit quando desabilitado`() {
        properties.enabled = false
        val filter = RateLimitingFilter(properties, objectMapper)

        repeat(10) {
            val (response, chain) = performRequest(filter, "/auth/login")
            assertNotNull(chain.request)
            assertEquals(HttpStatus.OK.value(), response.status)
        }
    }
}
