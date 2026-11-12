package br.com.tavaressan.cafey.auth

import br.com.tavaressan.cafey.exception.BadCredentialsException
import br.com.tavaressan.cafey.exception.RequisicaoInvalidaException
import br.com.tavaressan.cafey.exception.ResourceNotFoundException
import br.com.tavaressan.cafey.exception.ServicoIndisponivelException
import br.com.tavaressan.cafey.mail.EmailSenderService
import br.com.tavaressan.cafey.security.JwtTokenService
import br.com.tavaressan.cafey.security.MfaCodigosRecuperacaoService
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
    private val mfaCryptoService: MfaCryptoService,
    private val mfaCodigosRecuperacao: MfaCodigosRecuperacaoService
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

    /**
     * Gera um segredo novo e o guarda como **pendente**. O segredo em uso (se o 2FA já estiver ativo)
     * só é trocado quando o usuário confirmar o primeiro código em [ativar2fa]; desistir no meio não
     * tranca ninguém fora da conta.
     */
    @Transactional
    fun configurar2fa(usuarioId: UUID): MfaConfigurarResponse {
        exigirMfaDisponivel()
        val usuario = buscarUsuario(usuarioId)
        val segredo = mfaTotpService.generateSecretBase32()
        usuario.mfaSecretPendente = mfaCryptoService.encrypt(segredo)
        usuario.atualizadoEm = Instant.now()
        usuarioRepository.save(usuario)
        val otpauthUri = mfaTotpService.buildOtpAuthUri(segredo, usuario.email)
        return MfaConfigurarResponse(otpauthUri = otpauthUri, segredo = segredo)
    }

    /**
     * Confirma o segredo pendente com o primeiro código: ele passa a ser o segredo em uso, o 2FA fica
     * ativo e um conjunto novo de códigos de recuperação substitui o anterior.
     */
    @Transactional
    fun ativar2fa(usuarioId: UUID, request: MfaAtivarRequest): MfaAtivarResponse {
        exigirMfaDisponivel()
        val usuario = buscarUsuario(usuarioId)
        val pendenteCifrado = usuario.mfaSecretPendente
            ?: throw RequisicaoInvalidaException("2FA não configurado. Chame /auth/2fa/configurar antes de ativar.")
        if (!mfaTotpService.verifyCode(mfaCryptoService.decrypt(pendenteCifrado), request.codigo)) {
            throw BadCredentialsException("Código TOTP inválido")
        }
        usuario.mfaSecret = pendenteCifrado
        usuario.mfaSecretPendente = null
        usuario.mfaHabilitado = true
        usuario.atualizadoEm = Instant.now()
        usuarioRepository.save(usuario)
        val codigos = mfaCodigosRecuperacao.gerarNovos(usuarioId)
        return MfaAtivarResponse(mensagem = "2FA ativado com sucesso", codigosRecuperacao = codigos)
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
        exigirMfaDisponivel()
        if (!verificarSegundoFator(usuario, request.codigo)) {
            throw BadCredentialsException("Código inválido")
        }
        return createAuthResponse(usuario, UUID.randomUUID())
    }

    /**
     * Desliga o 2FA. Exige o código do autenticador ou um código de recuperação, para que um token de
     * acesso vazado não baste para remover o segundo fator.
     */
    @Transactional
    fun desativar2fa(usuarioId: UUID, request: MfaDesativarRequest) {
        exigirMfaDisponivel()
        val usuario = buscarUsuario(usuarioId)
        if (!usuario.mfaHabilitado || usuario.mfaSecret == null) {
            throw RequisicaoInvalidaException("2FA não está ativo")
        }
        if (!verificarSegundoFator(usuario, request.codigo)) {
            throw BadCredentialsException("Código inválido")
        }
        usuario.mfaHabilitado = false
        usuario.mfaSecret = null
        usuario.mfaSecretPendente = null
        usuario.atualizadoEm = Instant.now()
        usuarioRepository.save(usuario)
        mfaCodigosRecuperacao.removerTodos(usuarioId)
    }

    /** Código de 6 dígitos vai para o TOTP; qualquer outro formato é tratado como código de recuperação. */
    private fun verificarSegundoFator(usuario: Usuario, codigo: String): Boolean {
        val normalizado = codigo.trim()
        if (normalizado.length == 6 && normalizado.all { it.isDigit() }) {
            val segredo = mfaCryptoService.decrypt(usuario.mfaSecret!!)
            return mfaTotpService.verifyCode(segredo, normalizado)
        }
        return mfaCodigosRecuperacao.consumir(usuario.id!!, normalizado)
    }

    private fun exigirMfaDisponivel() {
        if (!mfaCryptoService.isAvailable()) {
            throw ServicoIndisponivelException(
                "2FA indisponível no servidor: chave de cifra MFA não configurada"
            )
        }
    }

    private fun buscarUsuario(usuarioId: UUID): Usuario =
        usuarioRepository.findById(usuarioId)
            .orElseThrow { ResourceNotFoundException("Usuário não encontrado") }

    @Transactional(readOnly = true)
    fun status2fa(usuarioId: UUID): MfaStatusResponse {
        val usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow { ResourceNotFoundException("Usuário não encontrado") }
        val restantes = if (usuario.mfaHabilitado) mfaCodigosRecuperacao.restantes(usuarioId) else 0
        return MfaStatusResponse(mfaHabilitado = usuario.mfaHabilitado, codigosRecuperacaoRestantes = restantes)
    }
}
