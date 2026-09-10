package br.com.tavaressan.cafey.shared.domain.validation

/**
 * Validação de formulário de conta — mora em `shared`, não na UI (spec §2.3), e espelha as
 * mesmas regras de `br.com.tavaressan.cafey.auth.AuthDto` no backend (`@NotBlank`, `@Email`,
 * `@Size(min = 6)`), para o usuário ver o erro antes de gastar uma chamada de rede.
 */
private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

sealed interface FieldError {
    data object Required : FieldError
    data object InvalidEmail : FieldError
    data object PasswordTooShort : FieldError
    /** Hora fora do formato HH:mm (mesmo regex de `CriarAgendamentoRequest.hora` no backend). */
    data object InvalidTime : FieldError
    /** Máscara `diasSemana` sem nenhum bit ligado (backend exige 1..127). */
    data object NoDaySelected : FieldError
}

object AuthValidation {
    const val MIN_PASSWORD_LENGTH = 6

    fun validateNome(nome: String): FieldError? =
        if (nome.isBlank()) FieldError.Required else null

    fun validateEmail(email: String): FieldError? = when {
        email.isBlank() -> FieldError.Required
        !EMAIL_REGEX.matches(email) -> FieldError.InvalidEmail
        else -> null
    }

    fun validateSenha(senha: String): FieldError? = when {
        senha.isBlank() -> FieldError.Required
        senha.length < MIN_PASSWORD_LENGTH -> FieldError.PasswordTooShort
        else -> null
    }
}

/** Erros de campo do formulário de login. `null` no valor = campo válido. */
data class LoginFormErrors(
    val email: FieldError? = null,
    val senha: FieldError? = null,
) {
    val isValid: Boolean get() = email == null && senha == null

    companion object {
        fun validate(email: String, senha: String): LoginFormErrors = LoginFormErrors(
            email = AuthValidation.validateEmail(email),
            senha = AuthValidation.validateSenha(senha),
        )
    }
}

/** Erros de campo do formulário de cadastro. */
data class RegisterFormErrors(
    val nome: FieldError? = null,
    val email: FieldError? = null,
    val senha: FieldError? = null,
) {
    val isValid: Boolean get() = nome == null && email == null && senha == null

    companion object {
        fun validate(nome: String, email: String, senha: String): RegisterFormErrors = RegisterFormErrors(
            nome = AuthValidation.validateNome(nome),
            email = AuthValidation.validateEmail(email),
            senha = AuthValidation.validateSenha(senha),
        )
    }
}
