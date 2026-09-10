package br.com.tavaressan.cafey.shared.domain.validation

/**
 * Validação do formulário de agendamento (APP-05, UC-10/UC-11) — mesmas regras de
 * `CriarAgendamentoRequest`/`AtualizarAgendamentoRequest` no backend (`@Pattern` de `hora`,
 * `@Min`/`@Max` de `diasSemana`), para o usuário ver o erro antes de gastar uma chamada de rede.
 */
private val HORA_REGEX = Regex("^([01]?[0-9]|2[0-3]):[0-5][0-9]$")

object ScheduleValidation {
    fun validateHora(hora: String): FieldError? = when {
        hora.isBlank() -> FieldError.Required
        !HORA_REGEX.matches(hora) -> FieldError.InvalidTime
        else -> null
    }

    fun validateDiasSemana(diasSemana: Short): FieldError? =
        if (diasSemana < 1 || diasSemana > 127) FieldError.NoDaySelected else null
}

/** Erros de campo do formulário de agendamento. `null` no valor = campo válido. */
data class ScheduleFormErrors(
    val hora: FieldError? = null,
    val diasSemana: FieldError? = null,
) {
    val isValid: Boolean get() = hora == null && diasSemana == null

    companion object {
        fun validate(hora: String, diasSemana: Short): ScheduleFormErrors = ScheduleFormErrors(
            hora = ScheduleValidation.validateHora(hora),
            diasSemana = ScheduleValidation.validateDiasSemana(diasSemana),
        )
    }
}
