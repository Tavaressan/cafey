package br.com.tavaressan.cafey.schedule

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import java.time.Instant
import java.util.UUID
import kotlin.reflect.KClass

// Opções do seletor "Desliga sozinha após" do app (4/6/8/10 min). Todas ficam abaixo do teto de
// segurança de 900s de ComandoRequest/firmware (kMaxDurationS).
val DURACOES_PREPARO_PERMITIDAS_S = setOf(240, 360, 480, 600)

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [DuracaoPreparoPermitidaValidator::class])
annotation class DuracaoPreparoPermitida(
    val message: String = "Duração de preparo deve ser 240, 360, 480 ou 600 segundos",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

class DuracaoPreparoPermitidaValidator : ConstraintValidator<DuracaoPreparoPermitida, Int?> {
    // null é válido: significa "usar a duração do dispositivo".
    override fun isValid(value: Int?, context: ConstraintValidatorContext): Boolean =
        value == null || value in DURACOES_PREPARO_PERMITIDAS_S
}

data class CriarAgendamentoRequest(
    @field:NotBlank(message = "Hora é obrigatória")
    @field:Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$", message = "Hora deve estar no formato HH:mm")
    val hora: String,

    @field:Min(value = 1, message = "Dias da semana deve ser entre 1 e 127")
    @field:Max(value = 127, message = "Dias da semana deve ser entre 1 e 127")
    val diasSemana: Short,

    val ativo: Boolean = true,

    @field:DuracaoPreparoPermitida
    val duracaoPreparoS: Int? = null
)

data class AtualizarAgendamentoRequest(
    @field:Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$", message = "Hora deve estar no formato HH:mm")
    val hora: String? = null,

    @field:Min(value = 1, message = "Dias da semana deve ser entre 1 e 127")
    @field:Max(value = 127, message = "Dias da semana deve ser entre 1 e 127")
    val diasSemana: Short? = null,

    val ativo: Boolean? = null,

    // null = não alterar (mesma semântica dos demais campos).
    @field:DuracaoPreparoPermitida
    val duracaoPreparoS: Int? = null
)

data class AgendamentoResponse(
    val id: UUID,
    val dispositivoId: UUID,
    val hora: String,
    val diasSemana: Short,
    val ativo: Boolean,
    val duracaoPreparoS: Int?,
    val criadoEm: Instant,
    val atualizadoEm: Instant
)
