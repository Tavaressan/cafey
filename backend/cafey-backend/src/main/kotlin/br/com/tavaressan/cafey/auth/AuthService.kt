package br.com.tavaressan.cafey.auth

import br.com.tavaressan.cafey.exception.BadCredentialsException
import br.com.tavaressan.cafey.exception.RequisicaoInvalidaException
import br.com.tavaressan.cafey.exception.ResourceNotFoundException
import br.com.tavaressan.cafey.exception.ServicoIndisponivelException
import br.com.tavaressan.cafey.mail.EmailSenderService
import br.com.tavaressan.cafey.security.JwtTokenService
import br.com.tavaressan.cafey.security.MfaCryptoService
import br.com.tavaressan.cafey.security.MfaTotpService
import br.com.tavaressan.cafey.security.PasswordResetToken
import br.com.tavaressan.cafey.security.PasswordResetTokenRepository
import br.com.tavaressan.cafey.security.RefreshToken
import br.com.tavaressan.cafey.security.RefreshTokenRepository
import br.com.tavaressan.cafey.security.UsuarioDetails
import br.com.tavaressan.cafey.user.Usuario
import br.com.tavaressan.cafey.user.UsuarioRepository
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
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
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val authenticationManager: AuthenticationManager,
    private val jwtTokenService: JwtTokenService,
    private val emailSenderService: EmailSenderService,
    private val mfaTotpService: MfaTotpService,
    private val mfaCryptoService: MfaCryptoService
) {

    private val logger = LoggerFactory.getLogger(AuthService::class.java)

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

    /**
     * Credenciais verificadas pelo Spring Security (`AuthenticationManager` -> `DaoAuthenticationProvider`
     * -> [UsuarioDetailsService] + `PasswordEncoder`). Falha de autenticação sai como
     * `AuthenticationException` e é traduzida em 401 pelo `GlobalExceptionHandler`.
     *
     * Com 2FA ativo (issue #260), não emite tokens finais: retorna `mfaRequerido=true` e um token de
     * desafio de curta duração para ser validado em POST /auth/2fa/validar. Sem 2FA, o fluxo permanece
     * idêntico para os clientes atuais.
     */
    @Transactional
    fun login(request: LoginRequest): AuthResponse {
        val authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(request.email, request.senha)
        )
        val usuario = (authentication.principal as UsuarioDetails).usuario

        if (usuario.mfaHabilitado) {
            if (!mfaCryptoService.isAvailable()) {
                throw ServicoIndisponivelException(
                    "2FA indisponível no servidor: não é possível concluir o login com segundo fator no momento"
                )
            }
            val desafioToken = jwtTokenService.generateMfaChallengeToken(usuario.id!!)
            return AuthResponse(mfaRequerido = true, desafioToken = desafioToken, expiresIn = 300)
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

    @Transactional
    fun solicitarRecuperacaoSenha(request: SolicitarRecuperacaoSenhaRequest): RecuperacaoSenhaResponse {
        val mensagemGenerica = "Se o email estiver cadastrado, as instruções foram enviadas."
        val normalizedEmail = request.email.trim().lowercase()
        val usuario = usuarioRepository.findByEmail(normalizedEmail)
            ?: return RecuperacaoSenhaResponse(mensagemGenerica)

        val rawToken = UUID.randomUUID().toString().replace("-", "")
        val tokenHash = jwtTokenService.hashToken(rawToken)

        val resetToken = PasswordResetToken(
            usuario = usuario,
            tokenHash = tokenHash,
            expiraEm = Instant.now().plus(1, ChronoUnit.HOURS)
        )
        passwordResetTokenRepository.save(resetToken)

        // O token só é entregue por e-mail — nunca na resposta da API (correção do achado de
        // segurança levantado na issue #105: sem isso, o endpoint público era um account takeover).
        // Falha de envio é apenas logada: não pode virar 5xx nem alterar a resposta, senão vira
        // oráculo de enumeração de contas.
        try {
            emailSenderService.enviarEmailRecuperacaoSenha(normalizedEmail, rawToken)
        } catch (ex: Exception) {
            logger.error("Falha ao enviar e-mail de recuperação de senha para {}", normalizedEmail, ex)
        }

        return RecuperacaoSenhaResponse(mensagem = mensagemGenerica)
    }

    @Transactional
    fun redefinirSenha(request: RedefinirSenhaRequest) {
        val tokenHash = jwtTokenService.hashToken(request.token.trim())
        val resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
            ?: throw BadCredentialsException("Token de recuperação inválido ou expirado")

        if (resetToken.utilizado || resetToken.expiraEm.isBefore(Instant.now())) {
            throw BadCredentialsException("Token de recuperação inválido ou expirado")
        }

        val usuario = resetToken.usuario
        usuario.senhaHash = passwordEncoder.encode(request.novaSenha)!!
        usuario.atualizadoEm = Instant.now()
        usuarioRepository.save(usuario)

        resetToken.utilizado = true
        passwordResetTokenRepository.save(resetToken)
    }

    @Transactional
    fun configurar2fa(usuarioId: UUID): MfaConfigurarResponse {
        if (!mfaCryptoService.isAvailable()) {
            throw ServicoIndisponivelException(
                "2FA indisponível no servidor: chave de cifra MFA não configurada"
            )
        }
        val usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow { ResourceNotFoundException("Usuário não encontrado") }
        val segredo = mfaTotpService.generateSecretBase32()
        usuario.mfaSecret = mfaCryptoService.encrypt(segredo)
        // Não habilita ainda: a ativação ocorre em /auth/2fa/ativar após validar o primeiro código.
        usuario.atualizadoEm = Instant.now()
        usuarioRepository.save(usuario)
        val otpauthUri = mfaTotpService.buildOtpAuthUri(segredo, usuario.email)
        return MfaConfigurarResponse(otpauthUri = otpauthUri, segredo = segredo)
    }

    @Transactional
    fun ativar2fa(usuarioId: UUID, request: MfaAtivarRequest) {
        if (!mfaCryptoService.isAvailable()) {
            throw ServicoIndisponivelException(
                "2FA indisponível no servidor: chave de cifra MFA não configurada"
            )
        }
        val usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow { ResourceNotFoundException("Usuário não encontrado") }
        val segredoCifrado = usuario.mfaSecret
            ?: throw RequisicaoInvalidaException("2FA não configurado. Chame /auth/2fa/configurar antes de ativar.")
        val segredo = mfaCryptoService.decrypt(segredoCifrado)
        if (!mfaTotpService.verifyCode(segredo, request.codigo)) {
            throw BadCredentialsException("Código TOTP inválido")
        }
        usuario.mfaHabilitado = true
        usuario.atualizadoEm = Instant.now()
        usuarioRepository.save(usuario)
    }

    @Transactional
    fun validar2fa(request: MfaValidarRequest): AuthResponse {
        val usuarioId = try {
            jwtTokenService.extrairUsuarioIdDeDesafioMfa(request.desafioToken)
        } catch (ex: Exception) {
            throw BadCredentialsException("Token de desafio MFA inválido ou expirado")
        }
        val usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow { BadCredentialsException("Token de desafio MFA inválido ou expirado") }
        if (!usuario.mfaHabilitado || usuario.mfaSecret == null) {
            throw BadCredentialsException("2FA não está ativo para este usuário")
        }
        val segredo = mfaCryptoService.decrypt(usuario.mfaSecret!!)
        if (!mfaTotpService.verifyCode(segredo, request.codigo)) {
            throw BadCredentialsException("Código TOTP inválido")
        }
        return createAuthResponse(usuario, UUID.randomUUID())
    }

    @Transactional(readOnly = true)
    fun status2fa(usuarioId: UUID): MfaStatusResponse {
        val usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow { ResourceNotFoundException("Usuário não encontrado") }
        return MfaStatusResponse(mfaHabilitado = usuario.mfaHabilitado)
    }
}
