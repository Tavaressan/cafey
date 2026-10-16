package br.com.tavaressan.cafey.shared.domain.validation

/**
 * Validação do formulário de dispositivo (cadastro e edição) — espelha `CriarDispositivoRequest` e
 * `AtualizarDispositivoRequest` no backend, para o usuário ver o erro antes de gastar uma chamada
 * de rede. Só o nome é obrigatório; nos campos numéricos, vazio vale e vira `null` no request (o
 * backend usa o padrão no cadastro e mantém o valor atual na edição).
 */
object DeviceValidation {
    // Mesmos limites de `@Min`/`@Max` em `CriarDispositivoRequest.duracaoPreparoS`.
    const val MIN_DURATION_S = 30
    const val MAX_DURATION_S = 900

    fun validateNome(nome: String): FieldError? =
        if (nome.isBlank()) FieldError.Required else null

    fun validateDuracaoPreparoS(value: String): FieldError? {
        if (value.isBlank()) return null
        val seconds = value.trim().toIntOrNull()
        return if (seconds == null || seconds !in MIN_DURATION_S..MAX_DURATION_S) {
            FieldError.OutOfRange(MIN_DURATION_S, MAX_DURATION_S)
        } else {
            null
        }
    }

    fun validateLimiarDescalcificacao(value: String): FieldError? {
        if (value.isBlank()) return null
        val threshold = value.trim().toIntOrNull()
        return if (threshold == null || threshold <= 0) FieldError.NotPositive else null
    }
}

/** Erros de campo do formulário de dispositivo. `null` no valor = campo válido. */
data class DeviceFormErrors(
    val nome: FieldError? = null,
    val duracaoPreparoS: FieldError? = null,
    val limiarDescalcificacao: FieldError? = null,
) {
    val isValid: Boolean get() = nome == null && duracaoPreparoS == null && limiarDescalcificacao == null

    companion object {
        fun validate(nome: String, duracaoPreparoS: String = "", limiarDescalcificacao: String = ""): DeviceFormErrors =
            DeviceFormErrors(
                nome = DeviceValidation.validateNome(nome),
                duracaoPreparoS = DeviceValidation.validateDuracaoPreparoS(duracaoPreparoS),
                limiarDescalcificacao = DeviceValidation.validateLimiarDescalcificacao(limiarDescalcificacao),
            )
    }
}
