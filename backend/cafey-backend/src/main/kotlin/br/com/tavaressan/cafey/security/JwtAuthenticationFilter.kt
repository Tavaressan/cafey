package br.com.tavaressan.cafey.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Autentica a requisição a partir do cabeçalho `Authorization: Bearer <token>` (RF-23).
 *
 * O filtro só decide **se** autentica; a validação de assinatura e expiração fica no
 * [JwtTokenService] (`NimbusJwtDecoder`). Para autenticar, o token precisa passar em
 * [JwtTokenService.validarToken] e em [JwtTokenService.extrairUsuarioId]. Não consulta o banco.
 *
 * - Sem `Bearer <token>`: segue a cadeia sem autenticar.
 * - Token inválido, expirado ou com `sub` inválido: limpa o contexto e segue a cadeia. Quem decide é
 *   a regra de autorização: rota protegida responde 401 (entry point do `SecurityConfig`) e rota
 *   pública é atendida normalmente.
 *
 * A autenticação gravada é a do `JwtAuthenticationConverter` (o mesmo do resource server), um
 * `JwtAuthenticationToken` já autenticado cujo principal é o `Jwt`; assim o
 * `@AuthenticationPrincipal jwt: Jwt` dos controllers continua funcionando sem mudanças.
 *
 * Instanciado dentro do `SecurityConfig`, e não como bean: o Spring Boot registra no servlet
 * container todo bean do tipo `Filter`, e o filtro rodaria duas vezes.
 */
class JwtAuthenticationFilter(
    private val jwtTokenService: JwtTokenService
) : OncePerRequestFilter() {

    private val authenticationConverter = JwtAuthenticationConverter()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = bearerTokenOf(request)
        if (token != null) {
            try {
                val jwt = jwtTokenService.validarToken(token)
                jwtTokenService.extrairUsuarioId(jwt)

                val context = SecurityContextHolder.createEmptyContext()
                context.authentication = authenticationConverter.convert(jwt)
                SecurityContextHolder.setContext(context)
            } catch (ex: JwtException) {
                SecurityContextHolder.clearContext()
            }
        }
        filterChain.doFilter(request, response)
    }

    /** O token do cabeçalho `Authorization: Bearer <token>`, ou `null` se o cabeçalho não tem esse formato ou o token é vazio. */
    private fun bearerTokenOf(request: HttpServletRequest): String? {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION) ?: return null
        if (!header.startsWith(BEARER_PREFIX, ignoreCase = true)) return null
        return header.substring(BEARER_PREFIX.length).trim().ifEmpty { null }
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
    }
}
