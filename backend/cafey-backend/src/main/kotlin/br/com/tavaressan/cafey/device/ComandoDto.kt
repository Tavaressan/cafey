package br.com.tavaressan.cafey.device

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

    @field:Min(value = 30, message = "Duração de preparo deve ter no mínimo 30s")
    val duracaoS: Int? = null
)

data class ComandoResponse(
    val comandoId: String,
    val acao: AcaoComando,
    val duracaoS: Int,
    val emitidoEm: Instant
)
