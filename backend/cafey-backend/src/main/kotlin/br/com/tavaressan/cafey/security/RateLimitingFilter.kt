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
import java.util.concurrent.atomic.AtomicLong
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

    /**
     * Bucket com carimbo do último acesso, usado para descartar entradas ociosas. A mutação de
     * [lastAccessMillis] só acontece dentro de operações atômicas de chave única do
     * [ConcurrentHashMap] (`compute`/`computeIfPresent`), então concorrência entre leitura (limpeza)
     * e escrita (nova requisição) na mesma chave é serializada pelo próprio mapa — nunca perdemos o
     * estado de um cliente que acabou de ser acessado.
     */
    private class BucketEntry(val bucket: Bucket, @Volatile var lastAccessMillis: Long)

    // Contenção de memória: sem limite, o mapa cresce com o número de IPs distintos, algo que quem
    // faz a requisição controla — um atacante poderia usar isso para exaustão de memória (o oposto
    // do que o rate limiting deveria prevenir). Duas políticas combinadas, sem dependência nova:
    // (1) expiração por inatividade: um bucket sem requisições há mais tempo que a maior janela de
    //     rate limit configurada não carrega informação útil (a janela já "zerou" naturalmente), e
    //     é seguro descartá-lo; (2) teto rígido de entradas: se mesmo assim o mapa crescer além do
    //     teto dentro da própria janela (rajada de muitos IPs distintos), descartamos as entradas
    //     menos recentemente acessadas até voltar ao teto. A limpeza é disparada a cada N
    //     requisições processadas (sem thread/scheduler adicional) e roda em O(tamanho do mapa).
    private val buckets = ConcurrentHashMap<String, BucketEntry>()
    private val requestCounter = AtomicLong(0)
    internal val maxBuckets = 10_000
    private val cleanupIntervalRequests = 100L

    /** Exposto apenas para teste (confirmar que o mapa respeita o teto de crescimento). */
    internal fun bucketCount(): Int = buckets.size

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
        val now = System.currentTimeMillis()
        val entry = buckets.compute(key) { _, existing ->
            if (existing == null) {
                BucketEntry(newBucket(routeLimit), now)
            } else {
                existing.lastAccessMillis = now
                existing
            }
        }!!

        if (requestCounter.incrementAndGet() % cleanupIntervalRequests == 0L) {
            cleanupBuckets(now)
        }

        val probe = entry.bucket.tryConsumeAndReturnRemaining(1)
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

    /** Maior janela configurada entre as rotas: além dela, um bucket parado não diz mais nada. */
    private fun idleTtlMillis(): Long = Duration.ofSeconds(
        maxOf(
            properties.loginPeriodSeconds,
            properties.registrarPeriodSeconds,
            properties.recuperarSenhaPeriodSeconds
        )
    ).toMillis()

    /**
     * Descarta entradas ociosas há mais que [idleTtlMillis] e, se ainda assim o mapa exceder
     * [maxBuckets] (rajada de IPs distintos dentro da própria janela), descarta as menos
     * recentemente acessadas até respeitar o teto. Cada remoção usa `computeIfPresent`, que é
     * atômico por chave no [ConcurrentHashMap]: uma requisição concorrente que acabou de tocar a
     * mesma chave (via `compute` em [doFilterInternal]) é serializada com a limpeza, então nunca
     * removemos um bucket que um cliente ativo acabou de usar.
     */
    private fun cleanupBuckets(nowMillis: Long) {
        val ttlMillis = idleTtlMillis()
        buckets.keys.toList().forEach { key ->
            buckets.computeIfPresent(key) { _, entry ->
                if (nowMillis - entry.lastAccessMillis >= ttlMillis) null else entry
            }
        }

        val overflow = buckets.size - maxBuckets
        if (overflow > 0) {
            buckets.entries
                .sortedBy { it.value.lastAccessMillis }
                .take(overflow)
                .forEach { (key, staleEntry) ->
                    buckets.computeIfPresent(key) { _, entry -> if (entry === staleEntry) null else entry }
                }
        }
    }
}
