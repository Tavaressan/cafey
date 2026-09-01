package br.com.cafey.auth

import br.com.cafey.exception.BadCredentialsException
import br.com.cafey.security.JwtTokenService
import br.com.cafey.security.RefreshToken
import br.com.cafey.security.RefreshTokenRepository
import br.com.cafey.user.Usuario
import br.com.cafey.user.UsuarioRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class AuthService(
    private val usuarioRepository: UsuarioRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenService: JwtTokenService
) {

    @Transactional
    fun registrar(request: RegisterRequest): AuthResponse {
        val normalizedEmail = request.email.trim().lowercase()
        if (usuarioRepository.existsByEmail(normalizedEmail)) {
            throw BadCredentialsException("Email já cadastrado")
        }

        val usuario = Usuario(
            nome = request.nome.trim(),
            email = normalizedEmail,
            senhaHash = passwordEncoder.encode(request.senha)!!
        )
        val savedUser = usuarioRepository.save(usuario)

        return createAuthResponse(savedUser, UUID.randomUUID())
    }

    @Transactional
    fun login(request: LoginRequest): AuthResponse {
        val normalizedEmail = request.email.trim().lowercase()
        val usuario = usuarioRepository.findByEmail(normalizedEmail)
            ?: throw BadCredentialsException("Email ou senha inválidos")

        if (!passwordEncoder.matches(request.senha, usuario.senhaHash)) {
            throw BadCredentialsException("Email ou senha inválidos")
        }

        return createAuthResponse(usuario, UUID.randomUUID())
    }

    @Transactional
    fun refresh(request: RefreshRequest): AuthResponse {
        val rawToken = request.refreshToken.trim()
        val tokenHash = jwtTokenService.hashToken(rawToken)

        val tokenEntity = refreshTokenRepository.findByTokenHash(tokenHash)
            ?: throw BadCredentialsException("Token inválido ou expirado")

        if (tokenEntity.revogado) {
            // Detecção de reuso: token já revogado foi apresentado novamente -> revoga família inteira
            refreshTokenRepository.revokeFamily(tokenEntity.familiaId)
            throw BadCredentialsException("Token revogado. Reuso detectado, todas as sessões da família foram invalidadas.")
        }

        if (tokenEntity.expiraEm.isBefore(Instant.now())) {
            tokenEntity.revogado = true
            refreshTokenRepository.save(tokenEntity)
            throw BadCredentialsException("Token expirado")
        }

        // Rotação: revoga o token atual e gera um novo mantendo o mesmo familia_id
        tokenEntity.revogado = true
        refreshTokenRepository.save(tokenEntity)

        return createAuthResponse(tokenEntity.usuario, tokenEntity.familiaId)
    }

    private fun createAuthResponse(usuario: Usuario, familiaId: UUID): AuthResponse {
        val accessToken = jwtTokenService.generateAccessToken(usuario.id!!, usuario.email)
        val rawRefreshToken = jwtTokenService.generateRefreshToken()
        val tokenHash = jwtTokenService.hashToken(rawRefreshToken)

        val refreshToken = RefreshToken(
            usuario = usuario,
            tokenHash = tokenHash,
            familiaId = familiaId,
            expiraEm = Instant.now().plus(7, ChronoUnit.DAYS)
        )
        refreshTokenRepository.save(refreshToken)

        return AuthResponse(
            accessToken = accessToken,
            refreshToken = rawRefreshToken
        )
    }
}
