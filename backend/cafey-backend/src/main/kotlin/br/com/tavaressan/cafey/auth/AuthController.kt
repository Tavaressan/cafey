package br.com.tavaressan.cafey.auth

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(name = "Autenticação", description = "Registro, login, 2FA (TOTP) e recuperação de senha.")
@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService
) {

    @SecurityRequirements
    @Operation(
        summary = "Registra um novo usuário e devolve os tokens de acesso",
        description = "Nome oficial da rota: `/auth/signup`. O caminho `/auth/registrar` é um alias " +
            "mantido por compatibilidade com os clientes já publicados e se comporta exatamente igual."
    )
    @ApiResponse(responseCode = "201", description = "Usuário registrado com sucesso")
    @ApiResponse(
        responseCode = "400",
        description = "Dados inválidos ou email já cadastrado",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))]
    )
    @PostMapping("/signup", "/registrar")
    fun signup(@Valid @RequestBody request: RegisterRequest): ResponseEntity<AuthResponse> {
        val response = authService.registrar(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @SecurityRequirements
    @Operation(
        summary = "Autentica um usuário com email e senha",
        description = "Nome oficial da rota: `/auth/signin`. O caminho `/auth/login` é um alias " +
            "mantido por compatibilidade com os clientes já publicados e se comporta exatamente igual. " +
            "As credenciais são verificadas pelo Spring Security (`AuthenticationManager`); " +
            "se válidas e sem 2FA, a resposta traz os tokens JWT. " +
            "Se o usuário tiver 2FA ativo (issue #260), responde HTTP 200 com `mfaRequerido=true` e " +
            "`desafioToken` (curta duração, 5 min) para validação em POST `/auth/2fa/validar`."
    )
    @ApiResponse(responseCode = "200", description = "Login efetuado com sucesso ou desafio MFA exigido")
    @ApiResponse(
        responseCode = "401",
        description = "Credenciais inválidas",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))]
    )
    @PostMapping("/signin", "/login")
    fun signin(@Valid @RequestBody request: LoginRequest): ResponseEntity<AuthResponse> {
        val response = authService.login(request)
        return ResponseEntity.ok(response)
    }

    @SecurityRequirements
    @Operation(summary = "Renova o access token a partir de um refresh token válido")
    @ApiResponse(responseCode = "200", description = "Tokens renovados com sucesso")
    @ApiResponse(
        responseCode = "401",
        description = "Refresh token inválido ou expirado",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))]
    )
    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): ResponseEntity<AuthResponse> {
        val response = authService.refresh(request)
        return ResponseEntity.ok(response)
    }

    @SecurityRequirements
    @Operation(summary = "Solicita a recuperação de senha, gerando um token de redefinição")
    @ApiResponse(responseCode = "200", description = "Solicitação processada")
    @PostMapping("/recuperar-senha")
    fun recuperarSenha(@Valid @RequestBody request: SolicitarRecuperacaoSenhaRequest): ResponseEntity<RecuperacaoSenhaResponse> {
        val response = authService.solicitarRecuperacaoSenha(request)
        return ResponseEntity.ok(response)
    }

    @SecurityRequirements
    @Operation(summary = "Redefine a senha a partir de um token de recuperação válido")
    @ApiResponse(responseCode = "200", description = "Senha redefinida com sucesso")
    @ApiResponse(
        responseCode = "400",
        description = "Token inválido, expirado ou senha inválida",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))]
    )
    @PostMapping("/redefinir-senha")
    fun redefinirSenha(@Valid @RequestBody request: RedefinirSenhaRequest): ResponseEntity<Map<String, String>> {
        authService.redefinirSenha(request)
        return ResponseEntity.ok(mapOf("mensagem" to "Senha redefinida com sucesso"))
    }

    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "Gera segredo TOTP e URI otpauth:// para configurar 2FA (RFC 6238)",
        description = "Requer autenticação. Gera um novo segredo TOTP e o guarda cifrado como **pendente**: " +
            "se o 2FA já estiver ativo, o segredo em uso continua valendo até a confirmação em " +
            "`/auth/2fa/ativar`. Retorna a URI `otpauth://...` (para QR code) e o segredo em Base32 para " +
            "apps autenticadores (Google Authenticator, Authy etc.)."
    )
    @ApiResponse(responseCode = "200", description = "Segredo TOTP gerado")
    @ApiResponse(
        responseCode = "401",
        description = "Token JWT ausente ou inválido",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))]
    )
    @PostMapping("/2fa/configurar")
    fun configurar2fa(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<MfaConfigurarResponse> {
        val usuarioId = UUID.fromString(jwt.subject)
        return ResponseEntity.ok(authService.configurar2fa(usuarioId))
    }

    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "Ativa o 2FA validando o primeiro código TOTP",
        description = "Requer autenticação e um segredo pendente gerado em `/auth/2fa/configurar`. " +
            "Valida o código de 6 dígitos, troca o segredo em uso pelo pendente, habilita o 2FA e devolve " +
            "10 códigos de recuperação novos (os anteriores deixam de valer). Os códigos só aparecem aqui."
    )
    @ApiResponse(responseCode = "200", description = "2FA ativado; códigos de recuperação na resposta")
    @ApiResponse(
        responseCode = "400",
        description = "2FA não configurado",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))]
    )
    @ApiResponse(
        responseCode = "401",
        description = "Token JWT ausente/inválido ou código TOTP inválido",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))]
    )
    @PostMapping("/2fa/ativar")
    fun ativar2fa(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: MfaAtivarRequest
    ): ResponseEntity<MfaAtivarResponse> {
        val usuarioId = UUID.fromString(jwt.subject)
        return ResponseEntity.ok(authService.ativar2fa(usuarioId, request))
    }

    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "Desativa o 2FA",
        description = "Requer autenticação e o código de 6 dígitos do app autenticador ou um código de " +
            "recuperação. Apaga o segredo TOTP e os códigos de recuperação."
    )
    @ApiResponse(responseCode = "200", description = "2FA desativado")
    @ApiResponse(
        responseCode = "400",
        description = "2FA não está ativo",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))]
    )
    @ApiResponse(
        responseCode = "401",
        description = "Token JWT ausente/inválido ou código inválido",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))]
    )
    @PostMapping("/2fa/desativar")
    fun desativar2fa(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: MfaDesativarRequest
    ): ResponseEntity<Map<String, String>> {
        val usuarioId = UUID.fromString(jwt.subject)
        authService.desativar2fa(usuarioId, request)
        return ResponseEntity.ok(mapOf("mensagem" to "2FA desativado"))
    }

    @SecurityRequirements
    @Operation(
        summary = "Valida o código TOTP após desafio de login e emite os tokens JWT finais",
        description = "Endpoint público. Recebe o `desafioToken` (curta duração, retornado pelo " +
            "`/auth/signin` quando `mfaRequerido=true`) e o código TOTP de 6 dígitos ou um código de " +
            "recuperação (`XXXXX-XXXXX`, uso único). " +
            "Se válido, emite `accessToken` e `refreshToken`."
    )
    @ApiResponse(responseCode = "200", description = "Código válido; tokens JWT emitidos")
    @ApiResponse(
        responseCode = "401",
        description = "Desafio ou código TOTP inválido/expirado",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))]
    )
    @PostMapping("/2fa/validar")
    fun validar2fa(@Valid @RequestBody request: MfaValidarRequest): ResponseEntity<AuthResponse> {
        val response = authService.validar2fa(request)
        return ResponseEntity.ok(response)
    }

    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Retorna o status do 2FA do usuário autenticado")
    @ApiResponse(responseCode = "200", description = "Status do 2FA")
    @ApiResponse(
        responseCode = "401",
        description = "Token JWT ausente ou inválido",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))]
    )
    @GetMapping("/2fa/status")
    fun status2fa(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<MfaStatusResponse> {
        val usuarioId = UUID.fromString(jwt.subject)
        return ResponseEntity.ok(authService.status2fa(usuarioId))
    }
}
