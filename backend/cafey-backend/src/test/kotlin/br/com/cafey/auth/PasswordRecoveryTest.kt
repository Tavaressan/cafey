package br.com.cafey.auth

import br.com.cafey.exception.BadCredentialsException
import br.com.cafey.security.JwtTokenService
import br.com.cafey.security.PasswordResetToken
import br.com.cafey.security.PasswordResetTokenRepository
import br.com.cafey.security.RefreshTokenRepository
import br.com.cafey.user.Usuario
import br.com.cafey.user.UsuarioRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class PasswordRecoveryTest {

    @Mock
    private lateinit var usuarioRepository: UsuarioRepository

    @Mock
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Mock
    private lateinit var passwordResetTokenRepository: PasswordResetTokenRepository

    @Mock
    private lateinit var passwordEncoder: PasswordEncoder

    @Mock
    private lateinit var jwtTokenService: JwtTokenService

    private lateinit var authService: AuthService

    private val userId = UUID.randomUUID()
    private lateinit var user: Usuario

    @BeforeEach
    fun setUp() {
        authService = AuthService(
            usuarioRepository,
            refreshTokenRepository,
            passwordResetTokenRepository,
            passwordEncoder,
            jwtTokenService
        )
        user = Usuario(id = userId, nome = "User", email = "user@cafey.com", senhaHash = "oldhash")
    }

    @Test
    fun `should generate password reset token for valid email`() {
        `when`(usuarioRepository.findByEmail("user@cafey.com")).thenReturn(user)
        `when`(jwtTokenService.hashToken(anyString())).thenReturn("hashed-token")
        `when`(passwordResetTokenRepository.save(any(PasswordResetToken::class.java))).thenAnswer { it.getArgument(0) }

        val req = SolicitarRecuperacaoSenhaRequest(email = "user@cafey.com")
        val res = authService.solicitarRecuperacaoSenha(req)

        assertNotNull(res.token)
        verify(passwordResetTokenRepository).save(any(PasswordResetToken::class.java))
    }

    @Test
    fun `should not fail or leak info for unregistered email`() {
        `when`(usuarioRepository.findByEmail("unknown@cafey.com")).thenReturn(null)

        val req = SolicitarRecuperacaoSenhaRequest(email = "unknown@cafey.com")
        val res = authService.solicitarRecuperacaoSenha(req)

        assertNull(res.token)
        verify(passwordResetTokenRepository, never()).save(any(PasswordResetToken::class.java))
    }

    @Test
    fun `should reset password successfully with valid token`() {
        val resetToken = PasswordResetToken(
            usuario = user,
            tokenHash = "valid-hash",
            expiraEm = Instant.now().plus(1, ChronoUnit.HOURS),
            utilizado = false
        )

        `when`(jwtTokenService.hashToken("raw-token")).thenReturn("valid-hash")
        `when`(passwordResetTokenRepository.findByTokenHash("valid-hash")).thenReturn(resetToken)
        `when`(passwordEncoder.encode("newpassword123")).thenReturn("newhash")
        `when`(usuarioRepository.save(any(Usuario::class.java))).thenAnswer { it.getArgument(0) }
        `when`(passwordResetTokenRepository.save(any(PasswordResetToken::class.java))).thenAnswer { it.getArgument(0) }

        val req = RedefinirSenhaRequest(token = "raw-token", novaSenha = "newpassword123")
        authService.redefinirSenha(req)

        assertEquals("newhash", user.senhaHash)
        assertTrue(resetToken.utilizado)
        verify(usuarioRepository).save(user)
        verify(passwordResetTokenRepository).save(resetToken)
    }

    @Test
    fun `should reject expired reset token`() {
        val resetToken = PasswordResetToken(
            usuario = user,
            tokenHash = "expired-hash",
            expiraEm = Instant.now().minus(10, ChronoUnit.MINUTES),
            utilizado = false
        )

        `when`(jwtTokenService.hashToken("expired-token")).thenReturn("expired-hash")
        `when`(passwordResetTokenRepository.findByTokenHash("expired-hash")).thenReturn(resetToken)

        val req = RedefinirSenhaRequest(token = "expired-token", novaSenha = "newpassword123")
        assertThrows<BadCredentialsException> {
            authService.redefinirSenha(req)
        }
    }

    @Test
    fun `should reject already used reset token`() {
        val resetToken = PasswordResetToken(
            usuario = user,
            tokenHash = "used-hash",
            expiraEm = Instant.now().plus(1, ChronoUnit.HOURS),
            utilizado = true
        )

        `when`(jwtTokenService.hashToken("used-token")).thenReturn("used-hash")
        `when`(passwordResetTokenRepository.findByTokenHash("used-hash")).thenReturn(resetToken)

        val req = RedefinirSenhaRequest(token = "used-token", novaSenha = "newpassword123")
        assertThrows<BadCredentialsException> {
            authService.redefinirSenha(req)
        }
    }
}
