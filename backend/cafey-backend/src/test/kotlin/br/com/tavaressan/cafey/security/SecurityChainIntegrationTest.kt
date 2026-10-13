package br.com.tavaressan.cafey.security

import br.com.tavaressan.cafey.user.Usuario
import br.com.tavaressan.cafey.user.UsuarioRepository
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Cadeia de segurança real (MockMvc contra o `SecurityFilterChain`), para as regras de
 * autenticação por token e de autorização de rotas (RF-23 a RF-25). Os casos de 401 são
 * exercitados por requisição HTTP para garantir que quem responde é o entry point do Security, e
 * não o handler de exceções do MVC.
 *
 * O par de chaves é fixado por `@DynamicPropertySource` para que o teste consiga emitir, com a
 * mesma chave do backend, um token expirado (o `generateAccessToken` sempre emite `exp` futuro).
 * Nenhum token nem credencial fica escrito no código: tudo é gerado na hora.
 *
 * Cada requisição usa um IP próprio, porque as rotas de autenticação passam pelo rate limit por IP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SecurityChainIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jwtTokenService: JwtTokenService,
    private val usuarioRepository: UsuarioRepository,
    private val passwordEncoder: PasswordEncoder,
    private val applicationContext: ApplicationContext
) {

    companion object {
        private val emissor = TestJwtIssuer()
        private val proximoIp = AtomicInteger(1)

        @JvmStatic
        @DynamicPropertySource
        fun chavesJwtDoTeste(registry: DynamicPropertyRegistry) {
            registry.add("cafey.jwt.private-key") { emissor.privateKeyBase64 }
            registry.add("cafey.jwt.public-key") { emissor.publicKeyBase64 }
        }

        /**
         * As 7 rotas públicas de `/auth`, cada uma com um corpo inválido: a validação recusa com 400,
         * o que prova que a requisição passou pela segurança e chegou ao controller (um 401 ou 403
         * viria da segurança).
         */
        @JvmStatic
        fun rotasPublicasDeAuth(): List<Arguments> = listOf(
            arguments("/auth/signin", """{"email":"","senha":""}"""),
            arguments("/auth/login", """{"email":"","senha":""}"""),
            arguments("/auth/signup", """{"nome":"","email":"","senha":""}"""),
            arguments("/auth/registrar", """{"nome":"","email":"","senha":""}"""),
            arguments("/auth/refresh", """{"refreshToken":""}"""),
            arguments("/auth/recuperar-senha", """{"email":""}"""),
            arguments("/auth/redefinir-senha", """{"token":"","novaSenha":""}""")
        )
    }

    /** Rota sob `/auth/` que NÃO está na lista de rotas públicas: só serve para provar que não é pública. */
    @RestController
    class AuthProbeController {
        @GetMapping("/auth/probe")
        fun probe(): String = "ok"
    }

    @TestConfiguration
    class ProbeConfig {
        @Bean
        fun authProbeController() = AuthProbeController()
    }

    private fun ipNovo(): String {
        val n = proximoIp.getAndIncrement()
        return "10.40.${n / 250}.${n % 250 + 1}"
    }

    private fun chamar(
        metodo: HttpMethod,
        caminho: String,
        authorization: String? = null,
        corpo: String? = null
    ): ResultActions {
        val builder = request(metodo, caminho).with { req -> req.remoteAddr = ipNovo(); req }
        if (authorization != null) {
            builder.header(HttpHeaders.AUTHORIZATION, authorization)
        }
        if (corpo != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(corpo)
        }
        return mockMvc.perform(builder)
    }

    private fun tokenValido() = jwtTokenService.generateAccessToken(UUID.randomUUID(), "test@cafey.com")

    private fun esperar401ProblemDetails(resultado: ResultActions) {
        resultado
            .andExpect(status().isUnauthorized)
            .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith("application/problem+json")))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.title").value("Unauthorized"))
            .andExpect(jsonPath("$.detail").value("Token JWT ausente ou inválido"))
    }

    // --- Rota protegida: todos os estados do cabeçalho Authorization -----------------------------

    @Test
    fun `rota protegida sem cabecalho Authorization responde 401 em Problem Details`() {
        esperar401ProblemDetails(chamar(HttpMethod.GET, "/dispositivos"))
    }

    @Test
    fun `rota protegida com esquema Basic responde 401`() {
        val credenciais = Base64.getEncoder().encodeToString("usuario:senha".toByteArray())

        esperar401ProblemDetails(chamar(HttpMethod.GET, "/dispositivos", "Basic $credenciais"))
    }

    @Test
    fun `rota protegida com Bearer vazio responde 401`() {
        esperar401ProblemDetails(chamar(HttpMethod.GET, "/dispositivos", "Bearer "))
    }

    @Test
    fun `rota protegida com Bearer malformado responde 401`() {
        esperar401ProblemDetails(chamar(HttpMethod.GET, "/dispositivos", "Bearer abc"))
    }

    @Test
    fun `rota protegida com assinatura adulterada responde 401`() {
        val (cabecalho, _, assinatura) = tokenValido().split(".")
        val outroPayload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"sub":"${UUID.randomUUID()}","exp":4102444800}""".toByteArray())

        esperar401ProblemDetails(chamar(HttpMethod.GET, "/dispositivos", "Bearer $cabecalho.$outroPayload.$assinatura"))
    }

    @Test
    fun `rota protegida com token assinado por outra chave responde 401`() {
        val tokenDeOutraChave = TestJwtIssuer().emitir(UUID.randomUUID().toString())

        esperar401ProblemDetails(chamar(HttpMethod.GET, "/dispositivos", "Bearer $tokenDeOutraChave"))
    }

    @Test
    fun `rota protegida com token expirado responde 401`() {
        esperar401ProblemDetails(chamar(HttpMethod.GET, "/dispositivos", "Bearer ${emissor.emitirExpirado()}"))
    }

    @Test
    fun `rota protegida com token de sub fora do formato UUID responde 401`() {
        esperar401ProblemDetails(
            chamar(HttpMethod.GET, "/dispositivos", "Bearer ${emissor.emitir(subject = "nao-e-um-uuid")}")
        )
    }

    @Test
    fun `rota protegida com token valido responde 200`() {
        chamar(HttpMethod.GET, "/dispositivos", "Bearer ${tokenValido()}")
            .andExpect(status().isOk)
    }

    // --- Rotas públicas: lista fechada --------------------------------------------------------------

    @ParameterizedTest(name = "POST {0} responde sem token")
    @MethodSource("rotasPublicasDeAuth")
    fun `cada rota publica de auth responde sem token`(rota: String, corpoInvalido: String) {
        chamar(HttpMethod.POST, rota, corpo = corpoInvalido).andExpect(status().isBadRequest)
    }

    @ParameterizedTest(name = "POST {0} responde com token invalido ou expirado no cabecalho")
    @MethodSource("rotasPublicasDeAuth")
    fun `cada rota publica de auth tambem responde com token invalido ou expirado no cabecalho`(
        rota: String,
        corpoInvalido: String
    ) {
        val cabecalhos = listOf("Bearer invalido", "Bearer ${emissor.emitirExpirado()}", "Bearer ${tokenValido()}x")

        cabecalhos.forEach { cabecalho ->
            chamar(HttpMethod.POST, rota, authorization = cabecalho, corpo = corpoInvalido)
                .andExpect(status().isBadRequest)
        }
    }

    @Test
    fun `login com credenciais validas funciona mesmo com token invalido no cabecalho`() {
        val senha = "senha-${UUID.randomUUID()}"
        val usuario = usuarioRepository.save(
            Usuario(
                nome = "Dona Cafeteira",
                email = "cadeia-${UUID.randomUUID()}@cafey.com",
                senhaHash = passwordEncoder.encode(senha)!!
            )
        )

        chamar(
            HttpMethod.POST,
            "/auth/login",
            authorization = "Bearer invalido",
            corpo = """{"email":"${usuario.email}","senha":"$senha"}"""
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
    }

    @Test
    fun `rota nova sob auth fora da lista nao fica publica automaticamente`() {
        chamar(HttpMethod.GET, "/auth/probe").andExpect(status().isUnauthorized)

        chamar(HttpMethod.GET, "/auth/probe", "Bearer ${tokenValido()}")
            .andExpect(status().isOk)
            .andExpect(content().string("ok"))
    }

    @Test
    fun `metodo fora do matcher de uma rota publica nao e publico`() {
        esperar401ProblemDetails(chamar(HttpMethod.GET, "/auth/login"))
    }

    @Test
    fun `actuator nao e publico`() {
        esperar401ProblemDetails(chamar(HttpMethod.GET, "/actuator/health"))
    }

    @Test
    fun `swagger e pagina de redefinicao de senha continuam publicos`() {
        chamar(HttpMethod.GET, "/v3/api-docs", "Bearer invalido").andExpect(status().isOk)
        chamar(HttpMethod.GET, "/swagger-ui/index.html").andExpect(status().isOk)
        chamar(HttpMethod.GET, "/redefinir-senha.html").andExpect(status().isOk)
    }

    // --- Composição da cadeia ---------------------------------------------------------------------

    @Test
    fun `o filtro JWT proprio roda uma unica vez e o filtro do resource server saiu da cadeia`() {
        val filtrosDaCadeia = applicationContext.getBean(FilterChainProxy::class.java)
            .filterChains.flatMap { it.filters }

        assertEquals(1, filtrosDaCadeia.count { it is JwtAuthenticationFilter })
        assertEquals(0, filtrosDaCadeia.count { it is BearerTokenAuthenticationFilter })
        // Bean do tipo Filter seria registrado de novo no servlet container e rodaria duas vezes.
        assertTrue(applicationContext.getBeansOfType(JwtAuthenticationFilter::class.java).isEmpty())
    }
}
