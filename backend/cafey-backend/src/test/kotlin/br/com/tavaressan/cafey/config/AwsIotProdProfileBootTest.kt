package br.com.tavaressan.cafey.config

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.security.KeyPairGenerator
import java.util.Base64

/**
 * Reproduz #159: o profile `prod` referenciava 4 placeholders `AWS_IOT_*` sem default,
 * então o boot falhava por placeholder não resolvido quando essas variáveis de ambiente
 * não estavam definidas (cenário real do deploy antes de todas serem configuradas).
 *
 * As chaves RSA externas são fornecidas via [DynamicPropertySource] só para isolar o
 * cenário: em `prod`, [br.com.tavaressan.cafey.security.JwtTokenService] já exige essas
 * chaves de propósito (ver `JwtTokenServiceTest`) — não é o comportamento sob teste aqui.
 */
@SpringBootTest
@ActiveProfiles("prod")
class AwsIotProdProfileBootTest {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun jwtKeys(registry: DynamicPropertyRegistry) {
            val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
            keyPairGenerator.initialize(2048)
            val keyPair = keyPairGenerator.generateKeyPair()
            registry.add("cafey.jwt.private-key") { Base64.getEncoder().encodeToString(keyPair.private.encoded) }
            registry.add("cafey.jwt.public-key") { Base64.getEncoder().encodeToString(keyPair.public.encoded) }
        }
    }

    @Test
    fun `sobe com profile prod ativo mesmo sem nenhuma variavel AWS_IOT definida`() {
        // Se o contexto não subir (placeholder não resolvido), o SpringBootTest falha aqui.
    }
}
