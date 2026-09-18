package br.com.tavaressan.cafey.security

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
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

    /**
     * `/test/internal` também responde 500 quando `TestController` está ausente (o catch-all
     * `GlobalExceptionHandler.handleAllExceptions` engole até `NoResourceFoundException`), então
     * essa rota não discrimina bean registrado de bean ausente. `/test/not-found` sim: 404 com
     * esse `detail` exato só sai de `handleResourceNotFound`, alcançável apenas através do
     * controller real.
     */
    @Test
    fun `test not-found continua acessivel fora do perfil prod`() {
        mockMvc.perform(get("/test/not-found"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.detail").value("Test resource not found"))
    }
}
