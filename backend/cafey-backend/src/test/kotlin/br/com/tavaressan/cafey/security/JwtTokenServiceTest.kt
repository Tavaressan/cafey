package br.com.tavaressan.cafey.security

import br.com.tavaressan.cafey.config.JwtKeyProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import org.springframework.security.oauth2.jwt.JwtException
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
    fun `validarToken devolve o Jwt de um token valido`() {
        val service = JwtTokenService()
        val usuarioId = UUID.randomUUID()

        val jwt = service.validarToken(service.generateAccessToken(usuarioId, "test@cafey.com"))

        assertEquals(usuarioId.toString(), jwt.subject)
        assertEquals("test@cafey.com", jwt.claims["email"])
    }

    @Test
    fun `validarToken rejeita token expirado`() {
        val emissor = TestJwtIssuer()

        assertThrows(JwtException::class.java) { emissor.service().validarToken(emissor.emitirExpirado()) }
    }

    @Test
    fun `validarToken rejeita token assinado por outra chave`() {
        val tokenDeOutraChave = JwtTokenService().generateAccessToken(UUID.randomUUID(), "test@cafey.com")

        assertThrows(JwtException::class.java) { JwtTokenService().validarToken(tokenDeOutraChave) }
    }

    @Test
    fun `validarToken rejeita token adulterado`() {
        val service = JwtTokenService()
        val (cabecalho, _, assinatura) = service.generateAccessToken(UUID.randomUUID(), "test@cafey.com").split(".")
        val outroPayload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"sub":"${UUID.randomUUID()}","exp":4102444800}""".toByteArray())

        assertThrows(JwtException::class.java) { service.validarToken("$cabecalho.$outroPayload.$assinatura") }
    }

    @Test
    fun `validarToken rejeita token malformado ou vazio`() {
        val service = JwtTokenService()

        listOf("", "abc", "a.b.c", "nao.e.um.jwt.valido").forEach { token ->
            assertThrows(JwtException::class.java, { service.validarToken(token) }, "token: '$token'")
        }
    }

    @Test
    fun `extrairUsuarioId devolve o sub como UUID`() {
        val service = JwtTokenService()
        val usuarioId = UUID.randomUUID()
        val token = service.generateAccessToken(usuarioId, "test@cafey.com")

        assertEquals(usuarioId, service.extrairUsuarioId(service.validarToken(token)))
        assertEquals(usuarioId, service.extrairUsuarioId(token))
    }

    @Test
    fun `extrairUsuarioId rejeita token sem sub`() {
        val emissor = TestJwtIssuer()
        val service = emissor.service()

        assertThrows(JwtException::class.java) { service.extrairUsuarioId(service.validarToken(emissor.emitir(subject = null))) }
        assertThrows(JwtException::class.java) { service.extrairUsuarioId(emissor.emitir(subject = null)) }
    }

    @Test
    fun `extrairUsuarioId rejeita sub que nao e um UUID`() {
        val emissor = TestJwtIssuer()
        val service = emissor.service()
        val token = emissor.emitir(subject = "nao-e-um-uuid")

        assertThrows(JwtException::class.java) { service.extrairUsuarioId(service.validarToken(token)) }
        assertThrows(JwtException::class.java) { service.extrairUsuarioId(token) }
    }

    @Test
    fun `extrairUsuarioId com token em texto tambem valida a assinatura e a expiracao`() {
        val emissor = TestJwtIssuer()

        assertThrows(JwtException::class.java) { emissor.service().extrairUsuarioId(emissor.emitirExpirado()) }
        assertThrows(JwtException::class.java) {
            JwtTokenService().extrairUsuarioId(emissor.emitir(subject = UUID.randomUUID().toString()))
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
