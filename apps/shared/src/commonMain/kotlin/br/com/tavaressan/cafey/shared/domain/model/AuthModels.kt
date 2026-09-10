package br.com.tavaressan.cafey.shared.domain.model

import kotlinx.serialization.Serializable

/**
 * Modelos de autenticação — espelham `br.com.tavaressan.cafey.auth.AuthDto` do backend
 * (`backend/cafey-backend/.../auth/AuthDto.kt`). Nomes de campo idênticos aos DTOs Kotlin do
 * backend: o Jackson do Spring serializa em camelCase, mesma convenção do `kotlinx.serialization`.
 */
@Serializable
data class RegisterRequest(
    val nome: String,
    val email: String,
    val senha: String,
)

@Serializable
data class LoginRequest(
    val email: String,
    val senha: String,
)

@Serializable
data class RefreshRequest(
    val refreshToken: String,
)

@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long = 900,
)

@Serializable
data class SolicitarRecuperacaoSenhaRequest(
    val email: String,
)

@Serializable
data class RedefinirSenhaRequest(
    val token: String,
    val novaSenha: String,
)

@Serializable
data class RecuperacaoSenhaResponse(
    val mensagem: String,
    val token: String? = null,
)
