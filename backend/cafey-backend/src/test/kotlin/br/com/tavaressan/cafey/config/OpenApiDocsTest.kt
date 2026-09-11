package br.com.tavaressan.cafey.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocsTest(@Autowired private val mockMvc: MockMvc) {

    @Test
    fun `v3 api-docs deve responder 200 sem autenticacao`() {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
    }

    @Test
    fun `schema deve declarar SecurityScheme bearerAuth`() {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
            .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
    }

    @Test
    fun `endpoint publico de login nao deve exigir security`() {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.paths['/auth/login'].post.security").isArray)
            .andExpect(jsonPath("$.paths['/auth/login'].post.security", org.hamcrest.Matchers.empty<Any>()))
    }

    @Test
    fun `schema deve declarar bearerAuth como requisito de seguranca global`() {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.security[0].bearerAuth").isArray)
    }
}
