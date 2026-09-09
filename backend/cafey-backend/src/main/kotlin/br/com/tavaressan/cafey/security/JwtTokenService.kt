package br.com.tavaressan.cafey.security

import br.com.tavaressan.cafey.config.JwtKeyProperties
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.env.Environment
import org.springframework.core.env.StandardEnvironment
import org.springframework.security.oauth2.jwt.*
import org.springframework.stereotype.Service
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.HexFormat
import java.util.UUID

@Service
@EnableConfigurationProperties(JwtKeyProperties::class)
class JwtTokenService(
    keyProperties: JwtKeyProperties = JwtKeyProperties(),
    environment: Environment = StandardEnvironment()
) {

    private val logger = LoggerFactory.getLogger(JwtTokenService::class.java)

    private val keyPair: KeyPair = resolveKeyPair(keyProperties, environment)
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

    /**
     * Decide a origem do par de chaves RSA: externa (via [JwtKeyProperties]) ou efêmera.
     *
     * Ambas ausentes fora do perfil `prod` -> gera par efêmero (fallback local/testes). Ambas
     * ausentes em `prod` -> falha o boot, pois um par efêmero em produção invalidaria todos os
     * tokens a cada restart silenciosamente. Apenas uma presente -> erro de configuração, falha o
     * boot. Ambas presentes -> carrega e valida o par externo.
     */
    private fun resolveKeyPair(keyProperties: JwtKeyProperties, environment: Environment): KeyPair {
        val privateKeyValue = keyProperties.privateKey?.takeIf { it.isNotBlank() }
        val publicKeyValue = keyProperties.publicKey?.takeIf { it.isNotBlank() }

        return when {
            privateKeyValue == null && publicKeyValue == null -> {
                if (environment.activeProfiles.contains("prod")) {
                    throw IllegalStateException(
                        "Nenhuma chave RSA externa configurada em perfil 'prod'. Defina as variáveis de " +
                            "ambiente CAFEY_JWT_PRIVATE_KEY e CAFEY_JWT_PUBLIC_KEY antes de subir em produção; " +
                            "o fallback de chave efêmera só é permitido fora de produção."
                    )
                }
                logger.warn(
                    "Nenhuma chave RSA externa configurada (cafey.jwt.private-key / cafey.jwt.public-key). " +
                        "Gerando par efêmero: tokens emitidos NÃO sobrevivem a um restart e instâncias " +
                        "diferentes não conseguirão validar tokens umas das outras. Use apenas em " +
                        "ambiente local/testes."
                )
                generateRsaKey()
            }

            privateKeyValue == null || publicKeyValue == null -> throw IllegalStateException(
                "Configuração de chave RSA incompleta: cafey.jwt.private-key e cafey.jwt.public-key " +
                    "devem ser ambas fornecidas ou ambas omitidas."
            )

            else -> loadExternalKeyPair(privateKeyValue, publicKeyValue)
        }
    }

    private fun loadExternalKeyPair(privateKeyValue: String, publicKeyValue: String): KeyPair {
        val keyFactory = KeyFactory.getInstance("RSA")

        val privateKey = try {
            keyFactory.generatePrivate(PKCS8EncodedKeySpec(decodeKeyBase64(privateKeyValue))) as RSAPrivateKey
        } catch (ex: Exception) {
            throw IllegalStateException(
                "Falha ao carregar cafey.jwt.private-key: esperado chave RSA privada em formato PKCS#8, Base64.",
                ex
            )
        }

        val publicKey = try {
            keyFactory.generatePublic(X509EncodedKeySpec(decodeKeyBase64(publicKeyValue))) as RSAPublicKey
        } catch (ex: Exception) {
            throw IllegalStateException(
                "Falha ao carregar cafey.jwt.public-key: esperado chave RSA pública em formato X.509, Base64.",
                ex
            )
        }

        require(privateKey.modulus.bitLength() >= 2048) {
            "Chave RSA privada configurada (cafey.jwt.private-key) tem apenas ${privateKey.modulus.bitLength()} bits; o mínimo exigido é 2048."
        }
        require(publicKey.modulus.bitLength() >= 2048) {
            "Chave RSA pública configurada (cafey.jwt.public-key) tem apenas ${publicKey.modulus.bitLength()} bits; o mínimo exigido é 2048."
        }
        require(privateKey.modulus == publicKey.modulus) {
            "As chaves RSA configuradas (cafey.jwt.private-key / cafey.jwt.public-key) não formam um par: os módulos são diferentes."
        }

        return KeyPair(publicKey, privateKey)
    }

    /**
     * Aceita tanto Base64 puro quanto Base64 com cabeçalhos PEM (`-----BEGIN...-----`/`-----END...-----`).
     */
    private fun decodeKeyBase64(value: String): ByteArray {
        val cleaned = value.lines()
            .filterNot { it.isBlank() || it.startsWith("-----") }
            .joinToString("")
            .trim()
        return Base64.getDecoder().decode(cleaned)
    }

    private fun generateRsaKey(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        return keyPairGenerator.generateKeyPair()
    }
}
