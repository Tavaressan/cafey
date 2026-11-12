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
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val tokenType: String = "Bearer",
    val expiresIn: Long = 900,
    val mfaRequerido: Boolean? = null,
    val desafioToken: String? = null,
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

@Serializable
data class MfaConfigurarResponse(
    val otpauthUri: String,
    val segredo: String,
)

@Serializable
data class MfaAtivarRequest(
    val codigo: String,
)

@Serializable
data class MfaValidarRequest(
    val desafioToken: String,
    val codigo: String,
)

@Serializable
data class MfaAtivarResponse(
    val mensagem: String = "",
    /** Códigos de uso único para entrar sem o app autenticador. Só vêm nesta resposta. */
    val codigosRecuperacao: List<String> = emptyList(),
)

@Serializable
data class MfaDesativarRequest(
    val codigo: String,
)

@Serializable
data class MfaStatusResponse(
    val mfaHabilitado: Boolean,
    val codigosRecuperacaoRestantes: Int = 0,
)
