package br.com.cafey.auth

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterRequest(
    @field:NotBlank(message = "Nome é obrigatório")
    val nome: String,

    @field:NotBlank(message = "Email é obrigatório")
    @field:Email(message = "Email inválido")
    val email: String,

    @field:NotBlank(message = "Senha é obrigatória")
    @field:Size(min = 6, message = "Senha deve ter no mínimo 6 caracteres")
    val senha: String
)

data class LoginRequest(
    @field:NotBlank(message = "Email é obrigatório")
    @field:Email(message = "Email inválido")
    val email: String,

    @field:NotBlank(message = "Senha é obrigatória")
    val senha: String
)

data class RefreshRequest(
    @field:NotBlank(message = "RefreshToken é obrigatório")
    val refreshToken: String
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long = 900
)

data class SolicitarRecuperacaoSenhaRequest(
    @field:NotBlank(message = "Email é obrigatório")
    @field:Email(message = "Email inválido")
    val email: String
)

data class RedefinirSenhaRequest(
    @field:NotBlank(message = "Token de recuperação é obrigatório")
    val token: String,

    @field:NotBlank(message = "Nova senha é obrigatória")
    @field:Size(min = 6, message = "Nova senha deve ter no mínimo 6 caracteres")
    val novaSenha: String
)

data class RecuperacaoSenhaResponse(
    val mensagem: String,
    val token: String? = null
)
