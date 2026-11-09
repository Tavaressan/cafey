package br.com.tavaressan.cafey.auth

import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
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
    // O token emitido pelo JwtTokenService tem 72 caracteres (dois UUIDs); 100 dá folga sem aceitar lixo.
    @field:Size(max = 100, message = "RefreshToken inválido")
    val refreshToken: String
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AuthResponse(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val tokenType: String = "Bearer",
    val expiresIn: Long = 900,
    /** true quando o usuário tem 2FA ativo e precisa validar o código TOTP (issue #260). */
    val mfaRequerido: Boolean? = null,
    /** Token temporário de desafio MFA (curta duração), usado em POST /auth/2fa/validar. */
    val desafioToken: String? = null
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
    val mensagem: String
)

data class MfaConfigurarResponse(
    val otpauthUri: String,
    /** Segredo em Base32 para configuração manual no app autenticador. */
    val segredo: String
)

data class MfaAtivarRequest(
    @field:NotBlank(message = "Código TOTP é obrigatório")
    @field:Pattern(regexp = "^\\d{6}$", message = "Código TOTP deve ter 6 dígitos")
    val codigo: String
)

data class MfaValidarRequest(
    @field:NotBlank(message = "Token de desafio é obrigatório")
    val desafioToken: String,

    @field:NotBlank(message = "Código TOTP é obrigatório")
    @field:Pattern(regexp = "^\\d{6}$", message = "Código TOTP deve ter 6 dígitos")
    val codigo: String
)

data class MfaStatusResponse(
    val mfaHabilitado: Boolean
)
