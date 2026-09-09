package br.com.tavaressan.cafey.security

import br.com.tavaressan.cafey.config.RateLimitProperties
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

/**
 * Rate limiting por IP/rota para os endpoints sensíveis de autenticação (BE-24).
 *
 * Buckets em memória (sem storage distribuído, fora de escopo). Cada bucket é identificado por
 * `IP:rota` e reabastecido de acordo com [RateLimitProperties]. Ao estourar a cota, responde
 * HTTP 429 com Problem Details (RFC 9457) e cabeçalho `Retry-After` em segundos.
 */
class RateLimitingFilter(
    private val properties: RateLimitProperties,
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {

    private val buckets = ConcurrentHashMap<String, Bucket>()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (!properties.enabled) {
            filterChain.doFilter(request, response)
            return
        }

        val routeLimit = routeLimitFor(request.requestURI)
        if (routeLimit == null) {
            filterChain.doFilter(request, response)
            return
        }

        // Sem proxy reverso confiável na topologia atual: usar remoteAddr diretamente. Não fazemos
        // parsing de X-Forwarded-For aqui de propósito — confiar nesse header sem um proxy validado
        // na frente permitiria bypass do rate limit por spoofing. Revisitar se um proxy for adicionado.
        val ip = request.remoteAddr
        val key = "$ip:${request.requestURI}"
        val bucket = buckets.computeIfAbsent(key) { newBucket(routeLimit) }

        val probe = bucket.tryConsumeAndReturnRemaining(1)
        if (probe.isConsumed) {
            filterChain.doFilter(request, response)
            return
        }

        val retryAfterSeconds = ceil(probe.nanosToWaitForRefill / 1_000_000_000.0)
            .toLong()
            .coerceAtLeast(1)

        val problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.TOO_MANY_REQUESTS,
            "Muitas requisições para esta rota. Tente novamente em $retryAfterSeconds segundo(s)."
        )
        problemDetail.title = "Too Many Requests"
        problemDetail.type = URI.create("about:blank")

        response.status = HttpStatus.TOO_MANY_REQUESTS.value()
        response.setHeader("Retry-After", retryAfterSeconds.toString())
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.characterEncoding = "UTF-8"
        response.writer.write(objectMapper.writeValueAsString(problemDetail))
    }

    private fun routeLimitFor(path: String): Pair<Long, Long>? = when (path) {
        "/auth/login" -> properties.loginCapacity to properties.loginPeriodSeconds
        "/auth/registrar" -> properties.registrarCapacity to properties.registrarPeriodSeconds
        "/auth/recuperar-senha" -> properties.recuperarSenhaCapacity to properties.recuperarSenhaPeriodSeconds
        else -> null
    }

    private fun newBucket(routeLimit: Pair<Long, Long>): Bucket {
        val (capacity, periodSeconds) = routeLimit
        val bandwidth = Bandwidth.builder()
            .capacity(capacity)
            .refillIntervally(capacity, Duration.ofSeconds(periodSeconds))
            .build()
        return Bucket.builder().addLimit(bandwidth).build()
    }
}
