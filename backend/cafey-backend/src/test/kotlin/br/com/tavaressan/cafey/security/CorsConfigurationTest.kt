package br.com.tavaressan.cafey.security

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
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
}
