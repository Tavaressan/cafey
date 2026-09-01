package br.com.cafey.schedule

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import java.time.Instant
import java.util.UUID

data class CriarAgendamentoRequest(
    @field:NotBlank(message = "Hora é obrigatória")
    @field:Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$", message = "Hora deve estar no formato HH:mm")
    val hora: String,

    @field:Min(value = 1, message = "Dias da semana deve ser entre 1 e 127")
    @field:Max(value = 127, message = "Dias da semana deve ser entre 1 e 127")
    val diasSemana: Short,

    val ativo: Boolean = true
)

data class AtualizarAgendamentoRequest(
    @field:Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$", message = "Hora deve estar no formato HH:mm")
    val hora: String? = null,

    @field:Min(value = 1, message = "Dias da semana deve ser entre 1 e 127")
    @field:Max(value = 127, message = "Dias da semana deve ser entre 1 e 127")
    val diasSemana: Short? = null,

    val ativo: Boolean? = null
)

data class AgendamentoResponse(
    val id: UUID,
    val dispositivoId: UUID,
    val hora: String,
    val diasSemana: Short,
    val ativo: Boolean,
    val criadoEm: Instant,
    val atualizadoEm: Instant
)
