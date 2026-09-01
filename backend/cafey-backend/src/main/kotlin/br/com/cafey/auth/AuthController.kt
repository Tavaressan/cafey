package br.com.cafey.auth

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/registrar")
    fun registrar(@Valid @RequestBody request: RegisterRequest): ResponseEntity<AuthResponse> {
        val response = authService.registrar(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<AuthResponse> {
        val response = authService.login(request)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): ResponseEntity<AuthResponse> {
        val response = authService.refresh(request)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/recuperar-senha")
    fun recuperarSenha(@Valid @RequestBody request: SolicitarRecuperacaoSenhaRequest): ResponseEntity<RecuperacaoSenhaResponse> {
        val response = authService.solicitarRecuperacaoSenha(request)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/redefinir-senha")
    fun redefinirSenha(@Valid @RequestBody request: RedefinirSenhaRequest): ResponseEntity<Map<String, String>> {
        authService.redefinirSenha(request)
        return ResponseEntity.ok(mapOf("mensagem" to "Senha redefinida com sucesso"))
    }
}
