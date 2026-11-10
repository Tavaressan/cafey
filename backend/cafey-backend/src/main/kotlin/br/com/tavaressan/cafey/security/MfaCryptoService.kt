package br.com.tavaressan.cafey.security

import br.com.tavaressan.cafey.config.MfaProperties
import br.com.tavaressan.cafey.exception.ServicoIndisponivelException
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.env.Environment
import org.springframework.core.env.StandardEnvironment
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cifra/decifra o segredo TOTP com AES-256-GCM. O valor persistido é Base64 de `IV (12 bytes) +
 * ciphertext+tag`, nunca texto claro. A chave é separada da chave RSA de JWT (ver [MfaProperties]).
 *
 * Sem chave configurada em `prod`, o serviço fica indisponível (não derruba o boot): operações de
 * 2FA respondem 503 e login de usuário com 2FA ativo falha fechado (issue #260, correção pós-merge).
 * Fora de `prod`, ausente = chave efêmera (dev/testes).
 */
@Service
@EnableConfigurationProperties(MfaProperties::class)
class MfaCryptoService(
    properties: MfaProperties = MfaProperties(),
    environment: Environment = StandardEnvironment()
) {
    private val logger = LoggerFactory.getLogger(MfaCryptoService::class.java)
    private val secureRandom = SecureRandom()
    private val secretKey: SecretKey? = resolveKey(properties, environment)

    fun isAvailable(): Boolean = secretKey != null

    private fun resolveKey(properties: MfaProperties, environment: Environment): SecretKey? {
        val raw = properties.encryptionKey?.takeIf { it.isNotBlank() }
        if (raw == null) {
            if (environment.activeProfiles.contains("prod")) {
                logger.error(
                    "CAFEY_MFA_ENCRYPTION_KEY ausente em perfil 'prod': o 2FA está INDISPONÍVEL. " +
                        "Defina CAFEY_MFA_ENCRYPTION_KEY (Base64 de 32 bytes; gere com 'openssl rand -base64 32') " +
                        "no ambiente da instância. O app sobe, mas /auth/2fa/* responde 503 e login de usuário " +
                        "com 2FA ativo falha fechado até a chave ser configurada."
                )
                return null
            }
            logger.warn(
                "Nenhuma chave de cifra MFA configurada (cafey.mfa.encryption-key). " +
                    "Gerando chave AES efêmera: segredos TOTP cifrados NÃO sobrevivem a um restart. " +
                    "Use apenas em ambiente local/testes."
            )
            val bytes = ByteArray(32)
            secureRandom.nextBytes(bytes)
            return SecretKeySpec(bytes, "AES")
        }
        val bytes = try {
            Base64.getDecoder().decode(raw.trim())
        } catch (ex: Exception) {
            throw IllegalStateException(
                "Falha ao carregar cafey.mfa.encryption-key: esperado Base64 de 32 bytes (AES-256).", ex
            )
        }
        require(bytes.size == 32) {
            "cafey.mfa.encryption-key deve decodificar para 32 bytes (AES-256); tamanho obtido: ${bytes.size}."
        }
        return SecretKeySpec(bytes, "AES")
    }

    fun encrypt(plaintext: String): String {
        val key = secretKey ?: throw ServicoIndisponivelException(
            "2FA indisponível no servidor: chave de cifra MFA não configurada"
        )
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val cipherText = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val packed = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, packed, 0, iv.size)
        System.arraycopy(cipherText, 0, packed, iv.size, cipherText.size)
        return Base64.getEncoder().encodeToString(packed)
    }

    fun decrypt(encoded: String): String {
        val key = secretKey ?: throw ServicoIndisponivelException(
            "2FA indisponível no servidor: chave de cifra MFA não configurada"
        )
        val packed = try {
            Base64.getDecoder().decode(encoded)
        } catch (ex: Exception) {
            throw IllegalStateException("Segredo MFA armazenado em formato inválido", ex)
        }
        require(packed.size > 12) { "Segredo MFA armazenado em formato inválido" }
        val iv = packed.copyOfRange(0, 12)
        val cipherText = packed.copyOfRange(12, packed.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        val plain = cipher.doFinal(cipherText)
        return String(plain, Charsets.UTF_8)
    }
}
