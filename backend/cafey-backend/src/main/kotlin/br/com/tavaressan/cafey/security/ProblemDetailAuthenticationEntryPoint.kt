package br.com.tavaressan.cafey.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import tools.jackson.databind.ObjectMapper
import java.net.URI

/**
 * Resposta a uma requisição não autenticada numa rota protegida (RF-24): sempre 401, nunca 403,
 * em Problem Details (RFC 9457) e com `WWW-Authenticate: Bearer`.
 *
 * Sem um entry point explícito, ao sair o `.oauth2ResourceServer` o Spring Security cai no
 * `Http403ForbiddenEntryPoint` e responderia 403 para quem não mandou token.
 */
class ProblemDetailAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper
) : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        val problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Token JWT ausente ou inválido")
        problemDetail.title = "Unauthorized"
        problemDetail.type = URI.create("about:blank")

        response.status = HttpStatus.UNAUTHORIZED.value()
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.characterEncoding = "UTF-8"
        response.writer.write(objectMapper.writeValueAsString(problemDetail))
    }
}
