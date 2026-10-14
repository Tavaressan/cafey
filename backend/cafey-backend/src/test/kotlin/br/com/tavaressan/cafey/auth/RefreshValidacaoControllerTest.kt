package br.com.tavaressan.cafey.auth

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/** Testes HTTP (MockMvc) da Bean Validation de `POST /auth/refresh` (issue #215). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class RefreshValidacaoControllerTest @Autowired constructor(
    private val mockMvc: MockMvc
) {

    private fun refresh(token: String): ResultActions =
        mockMvc.perform(
            post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"$token"}""")
        )

    @Test
    fun `deve responder 400 em problem details com refresh token de 101 caracteres`() {
        refresh("a".repeat(101))
            .andExpect(status().isBadRequest)
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("Validation failed: refreshToken: RefreshToken inválido"))
    }

    @Test
    fun `deve passar da validacao com refresh token de 72 caracteres`() {
        // Token desconhecido: a validação aceita e o serviço responde 401, não 400.
        refresh(UUID.randomUUID().toString() + UUID.randomUUID().toString())
            .andExpect(status().isUnauthorized)
    }
}
