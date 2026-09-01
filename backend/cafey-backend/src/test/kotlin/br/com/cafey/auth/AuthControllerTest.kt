package br.com.cafey.auth

import br.com.cafey.exception.GlobalExceptionHandler
import br.com.cafey.security.JwtTokenService
import br.com.cafey.security.RefreshToken
import br.com.cafey.security.RefreshTokenRepository
import br.com.cafey.user.Usuario
import br.com.cafey.user.UsuarioRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.MediaType
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

    private lateinit var passwordEncoder: BCryptPasswordEncoder
    private lateinit var jwtTokenService: JwtTokenService
    private lateinit var authService: AuthService
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        passwordEncoder = BCryptPasswordEncoder(12)
        jwtTokenService = JwtTokenService()
        authService = AuthService(usuarioRepository, refreshTokenRepository, passwordEncoder, jwtTokenService)
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
