package br.com.tavaressan.cafey.security

import br.com.tavaressan.cafey.config.JwtKeyProperties
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * Emite JWTs de teste com um par de chaves RSA conhecido, gerado na hora (nenhum token nem chave
 * fica escrito no código). Serve onde o [JwtTokenService] não basta: `generateAccessToken` sempre
 * emite `exp` daqui a 15 minutos e `sub` em UUID, e os testes precisam de token expirado e de `sub`
 * ausente ou fora do formato.
 *
 * [service] devolve um [JwtTokenService] configurado com o mesmo par, então os tokens emitidos aqui
 * são validados por ele; já um [JwtTokenService] sem chaves (par efêmero) rejeita esses tokens,
 * o que reproduz "assinado por outra chave".
 */
class TestJwtIssuer(private val keyPair: KeyPair = gerarPar()) {

    val privateKeyBase64: String = Base64.getEncoder().encodeToString(keyPair.private.encoded)
    val publicKeyBase64: String = Base64.getEncoder().encodeToString(keyPair.public.encoded)

    private val encoder = NimbusJwtEncoder(
        ImmutableJWKSet(
            JWKSet(
                RSAKey.Builder(keyPair.public as RSAPublicKey)
                    .privateKey(keyPair.private as RSAPrivateKey)
                    .keyID(UUID.randomUUID().toString())
                    .build()
            )
        )
    )

    fun service(): JwtTokenService =
        JwtTokenService(JwtKeyProperties(privateKey = privateKeyBase64, publicKey = publicKeyBase64))

    fun emitir(
        subject: String?,
        issuedAt: Instant = Instant.now(),
        expiresAt: Instant = issuedAt.plusSeconds(15 * 60)
    ): String {
        val claims = JwtClaimsSet.builder()
            .issuer("cafey-backend")
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .claim("email", "teste@cafey.com")
        if (subject != null) {
            claims.subject(subject)
        }
        return encoder.encode(JwtEncoderParameters.from(claims.build())).tokenValue
    }

    /** Token com `exp` cinco minutos no passado: bem além da tolerância de 60 s do decoder. */
    fun emitirExpirado(subject: String = UUID.randomUUID().toString()): String {
        val agora = Instant.now()
        return emitir(subject, issuedAt = agora.minusSeconds(20 * 60), expiresAt = agora.minusSeconds(5 * 60))
    }

    companion object {
        fun gerarPar(): KeyPair =
            KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    }
}
