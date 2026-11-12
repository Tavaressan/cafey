package br.com.tavaressan.cafey.notification.webpush

import java.net.URI
import java.security.KeyPair
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class WebPushVapid(
    private val par: KeyPair,
    private val subject: String,
    private val clock: Clock = Clock.systemUTC()
) {
    private data class TokenCache(val jwt: String, val exp: Instant)
    private val cache = ConcurrentHashMap<String, TokenCache>()
    val publicaB64: String = WebPushChaves.codificarBase64Url(
        WebPushChaves.publicaNaoComprimida(par.public as ECPublicKey)
    )

    fun autorizacao(endpoint: String): String {
        val origem = origem(endpoint)
        val agora = clock.instant()
        val emCache = cache[origem]
        if (emCache != null && emCache.exp.isAfter(agora.plusSeconds(60))) {
            return "vapid t=${emCache.jwt}, k=$publicaB64"
        }
        val exp = agora.plusSeconds(12 * 3600)
        val jwt = gerarJwt(origem, exp.epochSecond)
        cache[origem] = TokenCache(jwt, exp)
        return "vapid t=$jwt, k=$publicaB64"
    }

    fun gerarJwt(aud: String, expEpoch: Long): String {
        val header = """{"typ":"JWT","alg":"ES256"}"""
        val subEscapado = subject.replace("\\", "\\\\").replace("\"", "\\\"")
        val payload = """{"aud":"$aud","exp":$expEpoch,"sub":"$subEscapado"}"""
        val signingInput = "${b64u(header.toByteArray())}.${b64u(payload.toByteArray())}"
        val sig = Signature.getInstance("SHA256withECDSAinP1363Format")
        sig.initSign(par.private as ECPrivateKey)
        sig.update(signingInput.toByteArray(Charsets.US_ASCII))
        val assinatura = sig.sign()
        return "$signingInput.${b64u(assinatura)}"
    }

    companion object {
        fun origem(endpoint: String): String {
            val uri = URI(endpoint)
            val scheme = uri.scheme
            val host = uri.host
            val port = uri.port
            val temPorta = port != -1 && !((scheme == "https" && port == 443) || (scheme == "http" && port == 80))
            return if (temPorta) "$scheme://$host:$port" else "$scheme://$host"
        }

        private fun b64u(bytes: ByteArray) = WebPushChaves.codificarBase64Url(bytes)
    }
}
