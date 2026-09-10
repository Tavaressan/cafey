package br.com.tavaressan.cafey.security

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Cobre a issue #67 (APP-08): sem CORS configurado, o preflight do navegador para o app Web —
 * servido em origem diferente do backend (dev server do Kotlin/Wasm) — é bloqueado antes de
 * qualquer autenticação, e nenhum request cross-origin chega aos controllers.
 *
 * `http://localhost:8081` é a origem padrão de [br.com.tavaressan.cafey.config.CorsProperties]
 * (não sobrescrita em `src/test/resources/application.yml`).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CorsConfigurationTest @Autowired constructor(
    private val mockMvc: MockMvc
) {

    @Test
    fun `preflight de origem autorizada recebe Access-Control-Allow-Origin`() {
        mockMvc.perform(
            options("/auth/login")
                .header(HttpHeaders.ORIGIN, "http://localhost:8081")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Authorization, Content-Type")
        )
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:8081"))
    }

    @Test
    fun `preflight de origem nao autorizada e rejeitado`() {
        mockMvc.perform(
            options("/auth/login")
                .header(HttpHeaders.ORIGIN, "http://origem-nao-confiavel.com")
                .header("Access-Control-Request-Method", "POST")
        )
            .andExpect(status().isForbidden)
    }

    /**
     * O caso acima usa `/auth/login`, que é `permitAll()` — não exercita a interação entre o CORS e
     * `anyRequest().authenticated()`. Todo endpoint que o app Web usa depois do login é protegido,
     * então é aqui que o preflight precisa passar: o `CorsFilter` roda antes da autenticação, e o
     * preflight não carrega o header `Authorization`.
     */
    @Test
    fun `preflight em endpoint protegido passa sem autenticacao`() {
        mockMvc.perform(
            options("/dispositivos")
                .header(HttpHeaders.ORIGIN, "http://localhost:8081")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization")
        )
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:8081"))
    }

    @Test
    fun `preflight em endpoint protegido de origem nao autorizada e rejeitado`() {
        mockMvc.perform(
            options("/dispositivos")
                .header(HttpHeaders.ORIGIN, "http://origem-nao-confiavel.com")
                .header("Access-Control-Request-Method", "GET")
        )
            .andExpect(status().isForbidden)
    }

    /** Liberar o preflight não pode liberar o recurso: sem token, o 401 continua valendo. */
    @Test
    fun `requisicao real cross-origin sem token continua negada`() {
        mockMvc.perform(
            get("/dispositivos").header(HttpHeaders.ORIGIN, "http://localhost:8081")
        )
            .andExpect(status().isUnauthorized)
    }
}
