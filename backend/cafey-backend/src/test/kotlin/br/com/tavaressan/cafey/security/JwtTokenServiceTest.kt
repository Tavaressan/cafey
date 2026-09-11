package br.com.tavaressan.cafey.security

import br.com.tavaressan.cafey.config.JwtKeyProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import java.security.KeyPairGenerator
import java.util.Base64
import java.util.UUID

class JwtTokenServiceTest {

    private fun gerarParBase64(bits: Int = 2048): Pair<String, String> {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(bits)
        val keyPair = keyPairGenerator.generateKeyPair()
        val privateKeyBase64 = Base64.getEncoder().encodeToString(keyPair.private.encoded)
        val publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        return privateKeyBase64 to publicKeyBase64
    }

    @Test
    fun `deve gerar par efemero quando nenhuma chave externa e configurada`() {
        val service = JwtTokenService(JwtKeyProperties())
        val token = service.generateAccessToken(UUID.randomUUID(), "test@cafey.com")
        assertNotNull(token)
    }

    @Test
    fun `deve falhar o boot quando apenas a chave privada e informada`() {
        val (privateKeyBase64, _) = gerarParBase64()

        assertThrows(IllegalStateException::class.java) {
            JwtTokenService(JwtKeyProperties(privateKey = privateKeyBase64, publicKey = null))
        }
    }

    @Test
    fun `deve falhar o boot quando apenas a chave publica e informada`() {
        val (_, publicKeyBase64) = gerarParBase64()

        assertThrows(IllegalStateException::class.java) {
            JwtTokenService(JwtKeyProperties(privateKey = null, publicKey = publicKeyBase64))
        }
    }

    @Test
    fun `deve carregar par de chaves externas validas e emitir token`() {
        val (privateKeyBase64, publicKeyBase64) = gerarParBase64()

        val service = JwtTokenService(JwtKeyProperties(privateKey = privateKeyBase64, publicKey = publicKeyBase64))
        val token = service.generateAccessToken(UUID.randomUUID(), "test@cafey.com")
        val jwt = service.decoder.decode(token)

        assertEquals("test@cafey.com", jwt.claims["email"])
    }

    @Test
    fun `deve aceitar chaves externas com cabecalhos PEM`() {
        val (privateKeyBase64, publicKeyBase64) = gerarParBase64()
        val privateKeyPem = "-----BEGIN PRIVATE KEY-----\n$privateKeyBase64\n-----END PRIVATE KEY-----"
        val publicKeyPem = "-----BEGIN PUBLIC KEY-----\n$publicKeyBase64\n-----END PUBLIC KEY-----"

        val service = JwtTokenService(JwtKeyProperties(privateKey = privateKeyPem, publicKey = publicKeyPem))
        val token = service.generateAccessToken(UUID.randomUUID(), "test@cafey.com")
        assertNotNull(token)
    }

    @Test
    fun `deve falhar quando as chaves nao formam um par`() {
        val (privateKeyBase64, _) = gerarParBase64()
        val (_, outraPublicKeyBase64) = gerarParBase64()

        assertThrows(IllegalArgumentException::class.java) {
            JwtTokenService(JwtKeyProperties(privateKey = privateKeyBase64, publicKey = outraPublicKeyBase64))
        }
    }

    @Test
    fun `deve falhar quando a chave RSA e menor que 2048 bits`() {
        val (privateKeyBase64, publicKeyBase64) = gerarParBase64(bits = 1024)

        assertThrows(IllegalArgumentException::class.java) {
            JwtTokenService(JwtKeyProperties(privateKey = privateKeyBase64, publicKey = publicKeyBase64))
        }
    }

    @Test
    fun `deve falhar o boot quando nenhuma chave externa e configurada no perfil prod`() {
        val prodEnvironment = MockEnvironment().apply { setActiveProfiles("prod") }

        assertThrows(IllegalStateException::class.java) {
            JwtTokenService(JwtKeyProperties(), prodEnvironment)
        }
    }
}
