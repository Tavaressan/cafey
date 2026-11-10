package br.com.tavaressan.cafey.security

import br.com.tavaressan.cafey.user.Usuario
import br.com.tavaressan.cafey.user.UsuarioRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.security.KeyPairGenerator
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Correção pós-#260: com profile `prod` e sem `CAFEY_MFA_ENCRYPTION_KEY`, o app deve subir
 * (degradação do 2FA, não boot failure). `/auth/2fa/configurar` e `/auth/2fa/ativar` respondem 503;
 * login de usuário sem 2FA segue idêntico; login de usuário com 2FA ativo falha fechado (503, sem JWT).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class MfaProdDegradedIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val usuarioRepository: UsuarioRepository,
    private val passwordEncoder: PasswordEncoder
) {
    private companion object {
        const val SENHA = "senha-segura-123"
        val proximoIp = AtomicInteger(200)
        private val jwtKeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            // JWT exigido em prod; AWS IoT desabilitado (sem bean MQTT) — mesmo padrão dos testes prod.
            registry.add("cafey.jwt.private-key") {
                Base64.getEncoder().encodeToString(jwtKeyPair.private.encoded)
            }
            registry.add("cafey.jwt.public-key") {
                Base64.getEncoder().encodeToString(jwtKeyPair.public.encoded)
            }
            registry.add("aws.iot.endpoint") { "iot.test.invalid" }
            registry.add("aws.iot.certificate-path") { "false" }
            registry.add("aws.iot.private-key-path") { "false" }
            registry.add("aws.iot.root-ca-path") { "unused" }
            // CAFEY_MFA_ENCRYPTION_KEY deliberadamente ausente: 2FA degradado, app deve subir.
        }
    }

    private lateinit var usuarioSem2fa: Usuario

    @BeforeEach
    fun setUp() {
        usuarioSem2fa = usuarioRepository.save(
            Usuario(
                nome = "Sem 2FA",
                email = "sem2fa-${UUID.randomUUID()}@cafey.com",
                senhaHash = passwordEncoder.encode(SENHA)!!
            )
        )
    }

    private fun ipNovo() = "10.50.0.${proximoIp.getAndIncrement()}"

    private fun postJson(rota: String, corpo: String, bearer: String? = null) = mockMvc.perform(
        post(rota)
            .with { request -> request.remoteAddr = ipNovo(); request }
            .contentType(MediaType.APPLICATION_JSON)
            .content(corpo)
            .apply { if (bearer != null) header(HttpHeaders.AUTHORIZATION, "Bearer $bearer") }
    )

    private fun accessTokenDoUsuarioSem2fa(): String {
        val corpo = postJson("/auth/signin", """{"email":"${usuarioSem2fa.email}","senha":"$SENHA"}""")
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        return Regex("\"accessToken\"\\s*:\\s*\"([^\"]+)\"").find(corpo)!!.groupValues[1]
    }

    @Test
    fun `app sobe em prod sem chave mfa e endpoints 2fa respondem 503`() {
        val accessToken = accessTokenDoUsuarioSem2fa()

        postJson("/auth/2fa/configurar", "{}", bearer = accessToken)
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.title").value("Service Unavailable"))

        postJson("/auth/2fa/ativar", """{"codigo":"123456"}""", bearer = accessToken)
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.title").value("Service Unavailable"))
    }

    @Test
    fun `login de usuario sem 2fa segue identico em prod sem chave mfa`() {
        postJson("/auth/signin", """{"email":"${usuarioSem2fa.email}","senha":"$SENHA"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
            .andExpect(jsonPath("$.mfaRequerido").doesNotExist())
    }

    @Test
    fun `login de usuario com 2fa ativo falha fechado sem emitir jwt em prod sem chave mfa`() {
        val usuarioCom2fa = usuarioRepository.save(
            Usuario(
                nome = "Com 2FA",
                email = "com2fa-${UUID.randomUUID()}@cafey.com",
                senhaHash = passwordEncoder.encode(SENHA)!!,
                mfaHabilitado = true,
                mfaSecret = "qualquer-segredo-cifrado-armazenado"
            )
        )

        postJson("/auth/signin", """{"email":"${usuarioCom2fa.email}","senha":"$SENHA"}""")
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.title").value("Service Unavailable"))
            .andExpect(jsonPath("$.detail").isNotEmpty)
            .andExpect(jsonPath("$.accessToken").doesNotExist())
    }
}
