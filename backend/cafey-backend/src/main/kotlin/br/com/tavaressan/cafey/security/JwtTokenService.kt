package br.com.cafey.security

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import org.springframework.security.oauth2.jwt.*
import org.springframework.stereotype.Service
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.HexFormat
import java.util.UUID

@Service
class JwtTokenService {

    private val keyPair: KeyPair = generateRsaKey()
    private val rsaKey: RSAKey = RSAKey.Builder(keyPair.public as RSAPublicKey)
        .privateKey(keyPair.private as RSAPrivateKey)
        .keyID(UUID.randomUUID().toString())
        .build()

    private val encoder: JwtEncoder = NimbusJwtEncoder(ImmutableJWKSet(JWKSet(rsaKey)))
    val decoder: JwtDecoder = NimbusJwtDecoder.withPublicKey(keyPair.public as RSAPublicKey).build()

    fun generateAccessToken(usuarioId: UUID, email: String): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .issuer("cafey-backend")
            .issuedAt(now)
            .expiresAt(now.plus(15, ChronoUnit.MINUTES))
            .subject(usuarioId.toString())
            .claim("email", email)
            .build()

        return encoder.encode(JwtEncoderParameters.from(claims)).tokenValue
    }

    fun generateRefreshToken(): String {
        return UUID.randomUUID().toString() + UUID.randomUUID().toString()
    }

    fun hashToken(rawToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(rawToken.toByteArray(Charsets.UTF_8))
        return HexFormat.of().formatHex(hash)
    }

    private fun generateRsaKey(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        return keyPairGenerator.generateKeyPair()
    }
}
