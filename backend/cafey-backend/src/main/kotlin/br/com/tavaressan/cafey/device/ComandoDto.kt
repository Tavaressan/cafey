package br.com.tavaressan.cafey.device

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.time.Instant

enum class AcaoComando {
    LIGAR,
    DESLIGAR,
    CANCELAR
}

data class ComandoRequest(
    @field:NotBlank(message = "Ação é obrigatória")
    val acao: String,

    // Teto de segurança: o comando energiza a resistência de um aparelho na tomada, e nem o
    // backend nem o firmware limitavam a duração. 900s cobre com folga o default de 300s; deve
    // ser apertado quando a HW-06 (#8) medir o tempo real de extração.
    @field:Min(value = 30, message = "Duração de preparo deve ter no mínimo 30s")
    @field:Max(value = 900, message = "Duração de preparo deve ter no máximo 900s")
    val duracaoS: Int? = null
)

data class ComandoResponse(
    val comandoId: String,
    val acao: AcaoComando,
    val duracaoS: Int,
    val emitidoEm: Instant
)
