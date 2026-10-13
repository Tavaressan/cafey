package br.com.tavaressan.cafey.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

/**
 * Confirma, na cadeia de filtros real, que `/auth/signin` e `/auth/signup` estão registradas no
 * rate limit (RF-20) e que dividem o balde com os aliases `/auth/login` e `/auth/registrar`. O
 * [RateLimitingFilterTest] cobre a lógica do filtro; aqui o que se verifica é o registro do filtro
 * nas rotas (`RateLimitConfig`).
 *
 * O e-mail do corpo é inválido de propósito: a validação recusa com 400 sem passar pelo BCrypt, e o
 * rate limit conta a requisição antes disso. Cada teste usa o seu próprio IP, porque o contexto de
 * teste (e portanto o mapa de baldes) é compartilhado com outras classes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class RateLimitRegistrationTest @Autowired constructor(
    private val mockMvc: MockMvc
) {

    // O corpo de cadastro traz também o `nome`, que o de login não tem.
    private fun statusDe(rota: String, ip: String): Int {
        val corpo = if (rota.endsWith("signup") || rota.endsWith("registrar")) {
            """{"nome":"Rate Limit","email":"nao-e-email","senha":"password123"}"""
        } else {
            """{"email":"nao-e-email","senha":"password123"}"""
        }
        return mockMvc.perform(
            post(rota)
                .with { request -> request.remoteAddr = ip; request }
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo)
        ).andReturn().response.status
    }

    @Test
    fun `signin e login dividem o balde e a 6a tentativa do minuto recebe 429`() {
        val ip = "10.20.0.1"
        val rotas = listOf("/auth/signin", "/auth/login")

        repeat(5) { index -> assertEquals(400, statusDe(rotas[index % 2], ip)) }

        assertEquals(429, statusDe(rotas[5 % 2], ip))
    }

    @Test
    fun `signup e registrar dividem o balde e a 11a tentativa do minuto recebe 429`() {
        val ip = "10.20.0.2"
        val rotas = listOf("/auth/signup", "/auth/registrar")

        repeat(10) { index -> assertEquals(400, statusDe(rotas[index % 2], ip)) }

        assertEquals(429, statusDe(rotas[10 % 2], ip))
    }

    @Test
    fun `esgotar o balde de um IP nao limita outro IP`() {
        repeat(5) { assertEquals(400, statusDe("/auth/signin", "10.20.0.3")) }
        assertEquals(429, statusDe("/auth/signin", "10.20.0.3"))

        assertEquals(400, statusDe("/auth/signin", "10.20.0.4"))
    }
}
