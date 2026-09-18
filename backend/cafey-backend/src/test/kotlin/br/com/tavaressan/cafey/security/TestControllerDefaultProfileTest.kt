package br.com.tavaressan.cafey.security

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Complementa [TestControllerProdProfileTest]: a guarda `@Profile("!prod")` de
 * `TestController` (issue #185) não pode regredir o uso legítimo das rotas de teste fora de
 * produção (dev/test/CI) — o scaffolding continua registrado e acessível sem perfil ativo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class TestControllerDefaultProfileTest @Autowired constructor(
    private val mockMvc: MockMvc
) {

    @Test
    fun `test internal continua acessivel fora do perfil prod`() {
        mockMvc.perform(get("/test/internal"))
            .andExpect(status().isInternalServerError)
    }
}
