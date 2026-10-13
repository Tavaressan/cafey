package br.com.tavaressan.cafey.auth

import br.com.tavaressan.cafey.exception.GlobalExceptionHandler
import br.com.tavaressan.cafey.mail.FakeEmailSenderService
import br.com.tavaressan.cafey.security.JwtTokenService
import br.com.tavaressan.cafey.security.RefreshToken
import br.com.tavaressan.cafey.security.RefreshTokenRepository
import br.com.tavaressan.cafey.security.UsuarioDetailsService
import br.com.tavaressan.cafey.user.Usuario
import br.com.tavaressan.cafey.user.UsuarioRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.MediaType
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class AuthControllerTest {

    @Mock
    private lateinit var usuarioRepository: UsuarioRepository

    @Mock
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Mock
    private lateinit var passwordResetTokenRepository: br.com.tavaressan.cafey.security.PasswordResetTokenRepository

    private lateinit var emailSenderService: FakeEmailSenderService

    private lateinit var passwordEncoder: BCryptPasswordEncoder
    private lateinit var jwtTokenService: JwtTokenService
    private lateinit var authService: AuthService
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        passwordEncoder = BCryptPasswordEncoder(12)
        // Mesmo conjunto de colaboradores do bean de SecurityConfig: o login passa pelo
        // AuthenticationManager de verdade (DaoAuthenticationProvider + UserDetailsService).
        val authenticationProvider = DaoAuthenticationProvider(UsuarioDetailsService(usuarioRepository))
        authenticationProvider.setPasswordEncoder(passwordEncoder)
        jwtTokenService = JwtTokenService()
        emailSenderService = FakeEmailSenderService()
        authService = AuthService(
            usuarioRepository,
            refreshTokenRepository,
            passwordResetTokenRepository,
            passwordEncoder,
            ProviderManager(authenticationProvider),
            jwtTokenService,
            emailSenderService
        )
        val authController = AuthController(authService)

        mockMvc = MockMvcBuilders.standaloneSetup(authController)
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Test
    fun `should register new user successfully`() {
        `when`(usuarioRepository.existsByEmail("test@cafey.com")).thenReturn(false)
        `when`(usuarioRepository.save(any(Usuario::class.java))).thenAnswer { invocation ->
            val u = invocation.getArgument<Usuario>(0)
            u.id = UUID.randomUUID()
            u
        }
        `when`(refreshTokenRepository.save(any(RefreshToken::class.java))).thenAnswer { invocation ->
            invocation.getArgument<RefreshToken>(0)
        }

        mockMvc.perform(
            post("/auth/registrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nome":"Test User","email":"test@cafey.com","senha":"password123"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(900))
    }

    @Test
    fun `should login successfully with valid credentials`() {
        val user = Usuario(
            id = UUID.randomUUID(),
            nome = "Test User",
            email = "test@cafey.com",
            senhaHash = passwordEncoder.encode("password123")!!
        )
        `when`(usuarioRepository.findByEmail("test@cafey.com")).thenReturn(user)
        `when`(refreshTokenRepository.save(any(RefreshToken::class.java))).thenAnswer { invocation ->
            invocation.getArgument<RefreshToken>(0)
        }

        mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"test@cafey.com","senha":"password123"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
    }

    @Test
    fun `should fail login with invalid password`() {
        val user = Usuario(
            id = UUID.randomUUID(),
            nome = "Test User",
            email = "test@cafey.com",
            senhaHash = passwordEncoder.encode("password123")!!
        )
        `when`(usuarioRepository.findByEmail("test@cafey.com")).thenReturn(user)

        mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"test@cafey.com","senha":"wrongpassword"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.detail").value("Email ou senha inválidos"))
    }

    @Test
    fun `signin com credenciais validas devolve 200 com os dois tokens`() {
        val user = usuarioComSenha("password123")
        `when`(usuarioRepository.findByEmail("test@cafey.com")).thenReturn(user)
        `when`(refreshTokenRepository.save(any(RefreshToken::class.java))).thenAnswer { it.getArgument<RefreshToken>(0) }

        mockMvc.perform(
            post("/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"test@cafey.com","senha":"password123"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
    }

    @Test
    fun `signin normaliza o email antes de carregar o usuario`() {
        val user = usuarioComSenha("password123")
        `when`(usuarioRepository.findByEmail("test@cafey.com")).thenReturn(user)
        `when`(refreshTokenRepository.save(any(RefreshToken::class.java))).thenAnswer { it.getArgument<RefreshToken>(0) }

        mockMvc.perform(
            post("/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"Test@Cafey.COM","senha":"password123"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
    }

    @Test
    fun `signin com senha errada e com email inexistente devolvem o mesmo 401`() {
        `when`(usuarioRepository.findByEmail("test@cafey.com")).thenReturn(usuarioComSenha("password123"))
        `when`(usuarioRepository.findByEmail("ninguem@cafey.com")).thenReturn(null)

        val senhaErrada = mockMvc.perform(
            post("/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"test@cafey.com","senha":"wrongpassword"}""")
        ).andExpect(status().isUnauthorized).andReturn().response.contentAsString

        val emailInexistente = mockMvc.perform(
            post("/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"ninguem@cafey.com","senha":"password123"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.detail").value("Email ou senha inválidos"))
            .andReturn().response.contentAsString

        assertEquals(senhaErrada, emailInexistente)
    }

    @Test
    fun `falha ao carregar o usuario no signin vira 500 e nao 401`() {
        `when`(usuarioRepository.findByEmail("test@cafey.com")).thenThrow(IllegalStateException("banco fora do ar"))

        mockMvc.perform(
            post("/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"test@cafey.com","senha":"password123"}""")
        )
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
    }

    @Test
    fun `signup e registrar devolvem 201 com o mesmo contrato`() {
        `when`(usuarioRepository.existsByEmail("test@cafey.com")).thenReturn(false)
        `when`(usuarioRepository.save(any(Usuario::class.java))).thenAnswer { invocation ->
            val u = invocation.getArgument<Usuario>(0)
            u.id = UUID.randomUUID()
            u
        }
        `when`(refreshTokenRepository.save(any(RefreshToken::class.java))).thenAnswer { it.getArgument<RefreshToken>(0) }
        val corpo = """{"nome":"Test User","email":"test@cafey.com","senha":"password123"}"""

        listOf("/auth/signup", "/auth/registrar").forEach { rota ->
            mockMvc.perform(post(rota).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.accessToken").isNotEmpty)
                .andExpect(jsonPath("$.refreshToken").isNotEmpty)
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
        }
    }

    @Test
    fun `signup e registrar respondem igual quando o email ja esta cadastrado`() {
        `when`(usuarioRepository.existsByEmail("test@cafey.com")).thenReturn(true)
        val corpo = """{"nome":"Test User","email":"test@cafey.com","senha":"password123"}"""

        val respostas = listOf("/auth/signup", "/auth/registrar").map { rota ->
            mockMvc.perform(post(rota).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.detail").value("Email já cadastrado"))
                .andReturn().response.contentAsString
        }

        assertEquals(semInstance(respostas[0]), semInstance(respostas[1]))
    }

    @Test
    fun `signin e login respondem igual para credenciais validas e invalidas`() {
        `when`(usuarioRepository.findByEmail("test@cafey.com")).thenReturn(usuarioComSenha("password123"))
        `when`(refreshTokenRepository.save(any(RefreshToken::class.java))).thenAnswer { it.getArgument<RefreshToken>(0) }

        fun chamar(rota: String, senha: String) = mockMvc.perform(
            post(rota)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"test@cafey.com","senha":"$senha"}""")
        ).andReturn().response

        listOf("/auth/signin", "/auth/login").forEach { rota ->
            assertEquals(200, chamar(rota, "password123").status)
        }
        val recusas = listOf("/auth/signin", "/auth/login").map { chamar(it, "wrongpassword") }
        assertEquals(401, recusas[0].status)
        assertEquals(recusas[0].status, recusas[1].status)
        assertEquals(semInstance(recusas[0].contentAsString), semInstance(recusas[1].contentAsString))
    }

    /** O Problem Details traz o `instance` (caminho chamado), que muda entre o nome oficial e o alias. */
    private fun semInstance(corpo: String) = corpo.replace(Regex("\"instance\":\"[^\"]*\",?"), "")

    private fun usuarioComSenha(senha: String) = Usuario(
        id = UUID.randomUUID(),
        nome = "Test User",
        email = "test@cafey.com",
        senhaHash = passwordEncoder.encode(senha)!!
    )

    @Test
    fun `should rotate refresh token successfully`() {
        val user = Usuario(
            id = UUID.randomUUID(),
            nome = "Test User",
            email = "test@cafey.com",
            senhaHash = "hash"
        )
        val rawToken = "sample-refresh-token"
        val tokenHash = jwtTokenService.hashToken(rawToken)
        val familiaId = UUID.randomUUID()

        val tokenEntity = RefreshToken(
            id = UUID.randomUUID(),
            usuario = user,
            tokenHash = tokenHash,
            familiaId = familiaId,
            revogado = false,
            expiraEm = Instant.now().plus(7, ChronoUnit.DAYS)
        )

        `when`(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(tokenEntity)
        `when`(refreshTokenRepository.save(any(RefreshToken::class.java))).thenAnswer { invocation ->
            invocation.getArgument<RefreshToken>(0)
        }

        mockMvc.perform(
            post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"$rawToken"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)

        assertTrue(tokenEntity.revogado)
    }

    @Test
    fun `should detect reuse of already revoked refresh token and revoke family`() {
        val user = Usuario(
            id = UUID.randomUUID(),
            nome = "Test User",
            email = "test@cafey.com",
            senhaHash = "hash"
        )
        val rawToken = "already-revoked-token"
        val tokenHash = jwtTokenService.hashToken(rawToken)
        val familiaId = UUID.randomUUID()

        val tokenEntity = RefreshToken(
            id = UUID.randomUUID(),
            usuario = user,
            tokenHash = tokenHash,
            familiaId = familiaId,
            revogado = true, // already revoked
            expiraEm = Instant.now().plus(7, ChronoUnit.DAYS)
        )

        `when`(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(tokenEntity)

        mockMvc.perform(
            post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"$rawToken"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.detail").value("Token revogado. Reuso detectado, todas as sessões da família foram invalidadas."))
    }
}
