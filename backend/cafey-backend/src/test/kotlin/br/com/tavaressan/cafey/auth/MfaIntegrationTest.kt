package br.com.tavaressan.cafey.auth

import br.com.tavaressan.cafey.security.MfaTotpService
import br.com.tavaressan.cafey.user.Usuario
import br.com.tavaressan.cafey.user.UsuarioRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fluxo 2FA/TOTP ponta a ponta (issue #260): configuração, ativação, login com desafio pendente e
 * emissão de JWT após código TOTP válido. Cadeia de segurança real, banco H2.
 *
 * Sem `@Transactional` na classe: relê o usuário do banco após ativação. IPs únicos por requisição
 * para não colidir com o rate limit compartilhado no contexto de teste.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class MfaIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val usuarioRepository: UsuarioRepository,
    private val passwordEncoder: PasswordEncoder,
    private val objectMapper: ObjectMapper
) {
    private companion object {
        const val SENHA = "senha-segura-123"
        val proximoIp = AtomicInteger(100)
    }

    private val totpService = MfaTotpService()
    private lateinit var usuario: Usuario

    @BeforeEach
    fun setUp() {
        usuario = usuarioRepository.save(
            Usuario(
                nome = "Usuário MFA",
                email = "mfa-${UUID.randomUUID()}@cafey.com",
                senhaHash = passwordEncoder.encode(SENHA)!!
            )
        )
    }

    private fun ipNovo() = "10.40.0.${proximoIp.getAndIncrement()}"

    private fun postJson(rota: String, corpo: String, bearer: String? = null) = mockMvc.perform(
        post(rota)
            .with { request -> request.remoteAddr = ipNovo(); request }
            .contentType(MediaType.APPLICATION_JSON)
            .content(corpo)
            .apply { if (bearer != null) header(HttpHeaders.AUTHORIZATION, "Bearer $bearer") }
    )

    private fun login(email: String = usuario.email, senha: String = SENHA): String {
        val corpo = postJson("/auth/signin", """{"email":"$email","senha":"$senha"}""")
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        return objectMapper.readTree(corpo).get("accessToken").asText()
    }

    @Test
    fun `ativacao de 2fa, login com desafio pendente e emissao de jwt apos codigo totp valido`() {
        val accessToken = login()

        // Configura: gera segredo e retorna otpauth://
        val cfgCorpo = postJson("/auth/2fa/configurar", "{}", bearer = accessToken)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.otpauthUri").isNotEmpty)
            .andExpect(jsonPath("$.segredo").isNotEmpty)
            .andReturn().response.contentAsString
        val cfg = objectMapper.readTree(cfgCorpo)
        val segredo = cfg.get("segredo").asText()
        val otpauthUri = cfg.get("otpauthUri").asText()
        assertTrue(otpauthUri.startsWith("otpauth://totp/"))
        assertTrue(otpauthUri.contains("secret=$segredo"))

        val usuarioAposConfig = usuarioRepository.findByEmail(usuario.email)!!
        assertFalse(usuarioAposConfig.mfaHabilitado)
        // Fica pendente até a confirmação; o segredo em uso continua vazio.
        assertNull(usuarioAposConfig.mfaSecret)
        assertTrue(usuarioAposConfig.mfaSecretPendente != null)
        // Segredo persistido deve estar cifrado, não em Base32 claro.
        assertTrue(usuarioAposConfig.mfaSecretPendente != segredo)

        // Ativa com o primeiro código TOTP válido
        val codigo = totpService.generateCode(segredo)
        postJson("/auth/2fa/ativar", """{"codigo":"$codigo"}""", bearer = accessToken)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mensagem").value("2FA ativado com sucesso"))
            .andExpect(jsonPath("$.codigosRecuperacao.length()").value(10))

        val ativado = usuarioRepository.findByEmail(usuario.email)!!
        assertTrue(ativado.mfaHabilitado)
        assertTrue(ativado.mfaSecret != null)
        assertNull(ativado.mfaSecretPendente)

        // Login agora exige desafio MFA (sem tokens finais)
        val desafioCorpo = postJson("/auth/signin", """{"email":"${usuario.email}","senha":"$SENHA"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mfaRequerido").value(true))
            .andExpect(jsonPath("$.desafioToken").isNotEmpty)
            .andReturn().response.contentAsString
        val desafio = objectMapper.readTree(desafioCorpo)
        assertNull(desafio.get("accessToken"))
        assertNull(desafio.get("refreshToken"))
        val desafioToken = desafio.get("desafioToken").asText()

        // Valida o desafio com código TOTP e recebe os tokens finais
        val validoCorpo = postJson(
            "/auth/2fa/validar",
            """{"desafioToken":"$desafioToken","codigo":"${totpService.generateCode(segredo)}"}"""
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andReturn().response.contentAsString
        val tokenFinal = objectMapper.readTree(validoCorpo).get("accessToken").asText()

        // Token final acessa rota protegida
        mockMvc.perform(get("/dispositivos").header(HttpHeaders.AUTHORIZATION, "Bearer $tokenFinal"))
            .andExpect(status().isOk)
    }

    @Test
    fun `signin sem 2fa continua devolvendo tokens como antes`() {
        postJson("/auth/signin", """{"email":"${usuario.email}","senha":"$SENHA"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(900))
            .andExpect(jsonPath("$.mfaRequerido").doesNotExist())
            .andExpect(jsonPath("$.desafioToken").doesNotExist())
    }

    @Test
    fun `ativar 2fa com codigo invalido falha e nao habilita`() {
        val accessToken = login()
        postJson("/auth/2fa/configurar", "{}", bearer = accessToken).andExpect(status().isOk)

        postJson("/auth/2fa/ativar", """{"codigo":"000000"}""", bearer = accessToken)
            .andExpect(status().isUnauthorized)

        assertFalse(usuarioRepository.findByEmail(usuario.email)!!.mfaHabilitado)
    }

    @Test
    fun `validar 2fa com codigo invalido falha`() {
        val accessToken = login()
        val segredo = objectMapper.readTree(
            postJson("/auth/2fa/configurar", "{}", bearer = accessToken)
                .andExpect(status().isOk).andReturn().response.contentAsString
        ).get("segredo").asText()
        postJson("/auth/2fa/ativar", """{"codigo":"${totpService.generateCode(segredo)}"}""", bearer = accessToken)
            .andExpect(status().isOk)

        val desafioToken = objectMapper.readTree(
            postJson("/auth/signin", """{"email":"${usuario.email}","senha":"$SENHA"}""")
                .andExpect(status().isOk).andReturn().response.contentAsString
        ).get("desafioToken").asText()

        postJson("/auth/2fa/validar", """{"desafioToken":"$desafioToken","codigo":"000000"}""")
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `configurar e ativar 2fa exigem autenticacao`() {
        postJson("/auth/2fa/configurar", "{}").andExpect(status().isUnauthorized)
        postJson("/auth/2fa/ativar", """{"codigo":"123456"}""").andExpect(status().isUnauthorized)
    }

    /** Configura e ativa o 2FA; devolve o segredo e os códigos de recuperação. */
    private fun ativar2fa(accessToken: String): Pair<String, List<String>> {
        val segredo = configurar(accessToken)
        val corpo = postJson("/auth/2fa/ativar", """{"codigo":"${totpService.generateCode(segredo)}"}""", bearer = accessToken)
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val lista = objectMapper.readTree(corpo).get("codigosRecuperacao")
        val codigos = (0 until lista.size()).map { lista.get(it).asText() }
        return segredo to codigos
    }

    private fun configurar(accessToken: String): String = objectMapper.readTree(
        postJson("/auth/2fa/configurar", "{}", bearer = accessToken)
            .andExpect(status().isOk).andReturn().response.contentAsString
    ).get("segredo").asText()

    private fun desafio(): String = objectMapper.readTree(
        postJson("/auth/signin", """{"email":"${usuario.email}","senha":"$SENHA"}""")
            .andExpect(status().isOk).andReturn().response.contentAsString
    ).get("desafioToken").asText()

    private fun status2fa(accessToken: String) = objectMapper.readTree(
        mockMvc.perform(get("/auth/2fa/status").header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"))
            .andExpect(status().isOk).andReturn().response.contentAsString
    )

    @Test
    fun `reconfigurar sem confirmar mantem o segredo antigo valendo`() {
        val accessToken = login()
        val (segredoAntigo, _) = ativar2fa(accessToken)

        // Pede um segredo novo e desiste (não chama /ativar).
        val segredoNovo = configurar(accessToken)
        assertTrue(segredoNovo != segredoAntigo)
        assertTrue(usuarioRepository.findByEmail(usuario.email)!!.mfaHabilitado)

        // O autenticador antigo continua entrando.
        postJson("/auth/2fa/validar", """{"desafioToken":"${desafio()}","codigo":"${totpService.generateCode(segredoAntigo)}"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
    }

    @Test
    fun `reconfigurar confirmado troca o segredo e os codigos de recuperacao`() {
        val accessToken = login()
        val (segredoAntigo, codigosAntigos) = ativar2fa(accessToken)
        val (segredoNovo, codigosNovos) = ativar2fa(accessToken)
        assertTrue(codigosNovos.intersect(codigosAntigos.toSet()).isEmpty())

        postJson("/auth/2fa/validar", """{"desafioToken":"${desafio()}","codigo":"${codigosAntigos.first()}"}""")
            .andExpect(status().isUnauthorized)
        postJson("/auth/2fa/validar", """{"desafioToken":"${desafio()}","codigo":"${totpService.generateCode(segredoNovo)}"}""")
            .andExpect(status().isOk)
        // O segredo antigo só falha se gerar um código diferente do novo (colisão de 1 em 10^6).
        val codigoAntigo = totpService.generateCode(segredoAntigo)
        if (codigoAntigo != totpService.generateCode(segredoNovo)) {
            postJson("/auth/2fa/validar", """{"desafioToken":"${desafio()}","codigo":"$codigoAntigo"}""")
                .andExpect(status().isUnauthorized)
        }
    }

    @Test
    fun `codigo de recuperacao entra uma unica vez e aceita minusculas sem hifen`() {
        val accessToken = login()
        val (_, codigos) = ativar2fa(accessToken)
        assertEquals(10, codigos.size)
        assertEquals(10, status2fa(accessToken).get("codigosRecuperacaoRestantes").asInt())

        val codigo = codigos.first()
        assertTrue(codigo.matches(Regex("^[A-Z2-9]{5}-[A-Z2-9]{5}$")))
        postJson("/auth/2fa/validar", """{"desafioToken":"${desafio()}","codigo":"${codigo.replace("-", "").lowercase()}"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)

        postJson("/auth/2fa/validar", """{"desafioToken":"${desafio()}","codigo":"$codigo"}""")
            .andExpect(status().isUnauthorized)
        assertEquals(9, status2fa(accessToken).get("codigosRecuperacaoRestantes").asInt())
    }

    @Test
    fun `desativar com codigo do autenticador desliga o 2fa e o login volta a emitir tokens`() {
        val accessToken = login()
        val (segredo, _) = ativar2fa(accessToken)

        postJson("/auth/2fa/desativar", """{"codigo":"${totpService.generateCode(segredo)}"}""", bearer = accessToken)
            .andExpect(status().isOk)

        val desligado = usuarioRepository.findByEmail(usuario.email)!!
        assertFalse(desligado.mfaHabilitado)
        assertNull(desligado.mfaSecret)
        assertEquals(0, status2fa(accessToken).get("codigosRecuperacaoRestantes").asInt())
        postJson("/auth/signin", """{"email":"${usuario.email}","senha":"$SENHA"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.mfaRequerido").doesNotExist())
    }

    @Test
    fun `desativar com codigo de recuperacao funciona e com codigo errado nao`() {
        val accessToken = login()
        val (_, codigos) = ativar2fa(accessToken)

        postJson("/auth/2fa/desativar", """{"codigo":"000000"}""", bearer = accessToken)
            .andExpect(status().isUnauthorized)
        assertTrue(usuarioRepository.findByEmail(usuario.email)!!.mfaHabilitado)

        postJson("/auth/2fa/desativar", """{"codigo":"${codigos.last()}"}""", bearer = accessToken)
            .andExpect(status().isOk)
        assertFalse(usuarioRepository.findByEmail(usuario.email)!!.mfaHabilitado)
    }

    @Test
    fun `desativar sem 2fa ativo responde 400 e exige autenticacao`() {
        val accessToken = login()
        postJson("/auth/2fa/desativar", """{"codigo":"123456"}""", bearer = accessToken)
            .andExpect(status().isBadRequest)
        postJson("/auth/2fa/desativar", """{"codigo":"123456"}""").andExpect(status().isUnauthorized)
    }
}
