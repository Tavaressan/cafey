package br.com.tavaressan.cafey.security

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.security.KeyPairGenerator
import java.util.Base64

/**
 * Cobre a issue #185: `TestController` (scaffolding de dev para exercitar o
 * `GlobalExceptionHandler`) vivia em `src/main` liberado por `.permitAll()` em
 * `SecurityConfig`, sem nenhuma guarda de perfil — ficava acessível sem autenticação em
 * qualquer ambiente, incluindo produção. O fix restringe o bean com `@Profile("!prod")` e
 * retira o `.permitAll()` da rota em perfil `prod` — sem token, a resposta é 401, antes mesmo
 * de a requisição chegar ao `DispatcherServlet`.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class TestControllerProdProfileTest @Autowired constructor(
    private val mockMvc: MockMvc
) {

    companion object {
        // Par de chave RSA gerado em runtime só para satisfazer o boot de JwtTokenService em
        // perfil `prod` (que exige chave externa) — sem nenhum outro uso.
        private val jwtKeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

        @JvmStatic
        @DynamicPropertySource
        fun overrideProdOnlyProperties(registry: DynamicPropertyRegistry) {
            registry.add("cafey.jwt.private-key") {
                Base64.getEncoder().encodeToString(jwtKeyPair.private.encoded)
            }
            registry.add("cafey.jwt.public-key") {
                Base64.getEncoder().encodeToString(jwtKeyPair.public.encoded)
            }

            // application-prod.yml exige AWS_IOT_ENDPOINT/CERTIFICATE_PATH/PRIVATE_KEY_PATH/ROOT_CA_PATH
            // sem default (INFRA-05). Sobrescrever as chaves canônicas `aws.iot.*` evita a
            // PlaceholderResolutionException do binder; `certificate-path`/`private-key-path` = "false"
            // também desativa @ConditionalOnProperty do bean de conexão MQTT real (AwsIotConfig),
            // que não é relevante para este teste de segurança.
            registry.add("aws.iot.endpoint") { "iot.test.invalid" }
            registry.add("aws.iot.certificate-path") { "false" }
            registry.add("aws.iot.private-key-path") { "false" }
            registry.add("aws.iot.root-ca-path") { "unused" }
        }
    }

    @Test
    fun `test internal retorna 401 sem token em perfil prod`() {
        mockMvc.perform(get("/test/internal"))
            .andExpect(status().isUnauthorized)
    }
}
