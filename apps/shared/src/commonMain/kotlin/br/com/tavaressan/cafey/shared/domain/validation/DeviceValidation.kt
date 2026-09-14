package br.com.tavaressan.cafey.shared.domain.validation

/**
 * Validação do formulário de cadastro de dispositivo (APP-13) — espelha `CriarDispositivoRequest`
 * no backend (`nome` com `@NotBlank`), para o usuário ver o erro antes de gastar uma chamada de
 * rede. Recorte mínimo desta issue é cadastro manual: só o nome é obrigatório.
 */
object DeviceValidation {
    fun validateNome(nome: String): FieldError? =
        if (nome.isBlank()) FieldError.Required else null
}

/** Erros de campo do formulário de cadastro de dispositivo. `null` no valor = campo válido. */
data class DeviceFormErrors(val nome: FieldError? = null) {
    val isValid: Boolean get() = nome == null

    companion object {
        fun validate(nome: String): DeviceFormErrors = DeviceFormErrors(nome = DeviceValidation.validateNome(nome))
    }
}
