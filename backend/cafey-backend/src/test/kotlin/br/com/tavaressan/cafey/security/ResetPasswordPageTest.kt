package br.com.tavaressan.cafey.security

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Página estática de redefinição de senha servida pelo backend (issue #194, decisão B4): é o
 * destino do link enviado por e-mail, aberto sem JWT, e chama o endpoint existente
 * `POST /auth/redefinir-senha`.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ResetPasswordPageTest @Autowired constructor(
    private val mockMvc: MockMvc
) {

    @Test
    fun `pagina de redefinicao de senha e acessivel sem autenticacao`() {
        mockMvc.perform(get("/redefinir-senha.html").param("token", "abc123"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/auth/redefinir-senha")))
    }
}
