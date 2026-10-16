package br.com.tavaressan.cafey.shared.domain.validation

/**
 * Validação do formulário de dispositivo (cadastro e edição) — espelha `CriarDispositivoRequest` e
 * `AtualizarDispositivoRequest` no backend, para o usuário ver o erro antes de gastar uma chamada
 * de rede. Só o nome é obrigatório; nos campos numéricos, vazio vale e vira `null` no request (o
 * backend usa o padrão no cadastro e mantém o valor atual na edição).
 */
object DeviceValidation {
    // Mesmos limites de `@Min`/`@Max` em `CriarDispositivoRequest.duracaoPreparoS`.
    const val DURACAO_MIN_S = 30
    const val DURACAO_MAX_S = 900

    fun validateNome(nome: String): FieldError? =
        if (nome.isBlank()) FieldError.Required else null

    fun validateDuracaoPreparoS(valor: String): FieldError? {
        if (valor.isBlank()) return null
        val segundos = valor.trim().toIntOrNull()
        return if (segundos == null || segundos !in DURACAO_MIN_S..DURACAO_MAX_S) {
            FieldError.OutOfRange(DURACAO_MIN_S, DURACAO_MAX_S)
        } else {
            null
        }
    }

    fun validateLimiarDescalcificacao(valor: String): FieldError? {
        if (valor.isBlank()) return null
        val limiar = valor.trim().toIntOrNull()
        return if (limiar == null || limiar <= 0) FieldError.NotPositive else null
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
