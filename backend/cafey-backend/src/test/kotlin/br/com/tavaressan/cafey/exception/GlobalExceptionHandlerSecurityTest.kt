package br.com.tavaressan.cafey.exception

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.AuthenticationServiceException
import org.springframework.security.authentication.InternalAuthenticationServiceException
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.security.authentication.BadCredentialsException as SpringBadCredentialsException

/**
 * Cobre o tratamento das exceções do Spring Security pelo [GlobalExceptionHandler] (RF-19). Cada
 * caso passa pelo despacho do Spring MVC (controller de teste que lança a exceção), para exercitar
 * a escolha do handler mais específico, e não só a chamada direta de um método.
 */
class GlobalExceptionHandlerSecurityTest {

    @RestController
    class LancadorController {
        @GetMapping("/t/spring-bad-credentials")
        fun springBadCredentials(): String =
            throw SpringBadCredentialsException("detalhe interno que nao pode vazar")

        @GetMapping("/t/username-not-found")
        fun usernameNotFound(): String =
            throw UsernameNotFoundException("ninguem@cafey.com nao existe")

        @GetMapping("/t/authentication-service")
        fun authenticationService(): String =
            throw AuthenticationServiceException("falha de infraestrutura")

        @GetMapping("/t/internal-authentication-service")
        fun internalAuthenticationService(): String =
            throw InternalAuthenticationServiceException("banco fora do ar", IllegalStateException("db"))

        @GetMapping("/t/project-bad-credentials")
        fun projectBadCredentials(): String =
            throw BadCredentialsException("Mensagem definida pelo chamador")
    }

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(LancadorController())
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Test
    fun `BadCredentialsException do Spring Security vira 401 com mensagem generica`() {
        mockMvc.perform(get("/t/spring-bad-credentials"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.type").value("about:blank"))
            .andExpect(jsonPath("$.title").value("Unauthorized"))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.detail").value("Email ou senha inválidos"))
    }

    @Test
    fun `UsernameNotFoundException tambem vira o mesmo 401 sem revelar o email`() {
        mockMvc.perform(get("/t/username-not-found"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.detail").value("Email ou senha inválidos"))
    }

    @Test
    fun `AuthenticationServiceException vira 500 com o corpo do tratamento generico`() {
        mockMvc.perform(get("/t/authentication-service"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.type").value("about:blank"))
            .andExpect(jsonPath("$.title").value("Internal Server Error"))
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
    }

    @Test
    fun `InternalAuthenticationServiceException vira 500 e nao 401`() {
        mockMvc.perform(get("/t/internal-authentication-service"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.title").value("Internal Server Error"))
            .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
    }

    @Test
    fun `BadCredentialsException do projeto mantem o 401 com a mensagem do chamador`() {
        mockMvc.perform(get("/t/project-bad-credentials"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.title").value("Unauthorized"))
            .andExpect(jsonPath("$.detail").value("Mensagem definida pelo chamador"))
    }
}
