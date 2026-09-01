package br.com.cafey.device

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class CriarDispositivoRequest(
    @field:NotBlank(message = "Nome do dispositivo é obrigatório")
    val nome: String,

    val timezone: String? = null,

    @field:Min(value = 30, message = "Duração de preparo deve ter no mínimo 30s")
    val duracaoPreparoS: Int? = null,

    val limiarDescalcificacao: Int? = null
)

data class AtualizarDispositivoRequest(
    val nome: String? = null,
    val timezone: String? = null,
    val duracaoPreparoS: Int? = null,
    val limiarDescalcificacao: Int? = null
)

data class CompartilharDispositivoRequest(
    @field:NotBlank(message = "Email do convidado é obrigatório")
    @field:Email(message = "Email inválido")
    val email: String
)

data class DispositivoResponse(
    val id: UUID,
    val nome: String,
    val timezone: String,
    val estado: String,
    val online: Boolean,
    val ultimoVisto: Instant?,
    val versaoAgendamentos: Int,
    val duracaoPreparoS: Int,
    val limiarDescalcificacao: Int,
    val contadorPreparos: Int,
    val papel: PapelDispositivo,
    val criadoEm: Instant,
    val atualizadoEm: Instant
)

data class CompartilhamentoResponse(
    val usuarioId: UUID,
    val nome: String,
    val email: String,
    val papel: PapelDispositivo,
    val compartilhadoEm: Instant
)
