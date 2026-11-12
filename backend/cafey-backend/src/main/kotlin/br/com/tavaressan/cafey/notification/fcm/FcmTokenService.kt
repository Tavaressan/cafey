package br.com.tavaressan.cafey.notification.fcm

import com.fasterxml.jackson.annotation.JsonProperty
import tools.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
private val TOKEN_REUSE_BUFFER = Duration.ofSeconds(60)

/**
 * Obtém e renova o access token OAuth2 do Firebase via JWT RS256 (#362).
 *
 * Thread-safe: múltiplas threads do pool de push reutilizam o mesmo token sem corrida.
 * O token é descartado explicitamente em caso de 401 para forçar renovação imediata.
 */
internal class FcmTokenService(
    private val credenciais: FcmCredenciais,
    private val httpClient: HttpClient,
    private val mapper: ObjectMapper,
    private val clock: Clock = Clock.systemUTC()
) {
    private val logger = LoggerFactory.getLogger(FcmTokenService::class.java)
    private val lock = ReentrantLock()
    private var cachedToken: String? = null
    private var expiresAt: Instant = Instant.EPOCH

    private val privateKey = parsePrivateKey(credenciais.privateKey)

    private fun parsePrivateKey(pemValue: String): java.security.PrivateKey {
        val pem = pemValue
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val bytes = Base64.getDecoder().decode(pem)
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(bytes))
    }

    fun obterToken(): String = lock.withLock {
        val agora = clock.instant()
        val cached = cachedToken
        if (cached != null && agora.isBefore(expiresAt)) {
            return cached
        }
        return solicitarNovoToken()
    }

    /** Chamado pelo canal após receber 401: descarta o cache e obtém novo token na próxima chamada. */
    fun invalidar() = lock.withLock {
        cachedToken = null
        expiresAt = Instant.EPOCH
        logger.debug("Access token FCM invalidado (recebemos 401)")
    }

    private fun solicitarNovoToken(): String {
        val iat = clock.instant().epochSecond
        val exp = iat + 3600L
        val jwt = construirJwt(iat, exp)

        val body = "grant_type=${encode("urn:ietf:params:oauth:grant-type:jwt-bearer")}&assertion=${encode(jwt)}"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(credenciais.tokenUri))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofSeconds(10))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw IllegalStateException("Falha ao obter token FCM: HTTP ${response.statusCode()}")
        }
        val tokenResponse = mapper.readValue(response.body(), FcmTokenResponse::class.java)
        cachedToken = tokenResponse.accessToken
        expiresAt = clock.instant().plusSeconds(tokenResponse.expiresIn).minus(TOKEN_REUSE_BUFFER)
        logger.debug("Novo access token FCM obtido; expira em {}s", tokenResponse.expiresIn)
        return tokenResponse.accessToken
    }

    private fun construirJwt(iat: Long, exp: Long): String {
        val headerJson = """{"alg":"RS256","typ":"JWT"}"""
        val payloadJson = """{"iss":"${credenciais.clientEmail}","scope":"$SCOPE","aud":"${credenciais.tokenUri}","iat":$iat,"exp":$exp}"""
        val headerB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.toByteArray())
        val payloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.toByteArray())
        val signing = "$headerB64.$payloadB64"
        val sig = Signature.getInstance("SHA256withRSA").apply {
            initSign(privateKey)
            update(signing.toByteArray())
        }.sign()
        val sigB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(sig)
        return "$signing.$sigB64"
    }

    private fun encode(value: String) = java.net.URLEncoder.encode(value, Charsets.UTF_8)
}

private data class FcmTokenResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("expires_in") val expiresIn: Long
)
