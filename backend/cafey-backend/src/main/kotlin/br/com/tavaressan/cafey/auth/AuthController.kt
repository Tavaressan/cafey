package br.com.tavaressan.cafey.auth

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Autenticação", description = "Registro, login e recuperação de senha. Endpoints públicos.")
@SecurityRequirements
@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService
) {

    @Operation(summary = "Registra um novo usuário e devolve os tokens de acesso")
    @ApiResponse(responseCode = "201", description = "Usuário registrado com sucesso")
    @ApiResponse(
        responseCode = "400",
        description = "Dados inválidos ou email já cadastrado",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))]
    )
    @PostMapping("/registrar")
    fun registrar(@Valid @RequestBody request: RegisterRequest): ResponseEntity<AuthResponse> {
        val response = authService.registrar(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @Operation(summary = "Autentica um usuário com email e senha")
    @ApiResponse(responseCode = "200", description = "Login efetuado com sucesso")
    @ApiResponse(
        responseCode = "401",
        description = "Credenciais inválidas",
        content = [Content(schema = Schema(implementation = ProblemDetail::class))]
    )
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<AuthResponse> {
        val response = authService.login(request)
        return ResponseEntity.ok(response)
    }

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

    @Operation(summary = "Solicita a recuperação de senha, gerando um token de redefinição")
    @ApiResponse(responseCode = "200", description = "Solicitação processada")
    @PostMapping("/recuperar-senha")
    fun recuperarSenha(@Valid @RequestBody request: SolicitarRecuperacaoSenhaRequest): ResponseEntity<RecuperacaoSenhaResponse> {
        val response = authService.solicitarRecuperacaoSenha(request)
        return ResponseEntity.ok(response)
    }

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
}
