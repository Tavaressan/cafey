package br.com.tavaressan.cafey.shared.domain.model

import kotlinx.serialization.Serializable

/** Espelha `br.com.tavaressan.cafey.schedule.ScheduleDto`. `diasSemana` é uma máscara de bits
 * (1–127): bit 0 = domingo … bit 6 = sábado, igual ao `CriarAgendamentoRequest` do backend. */
@Serializable
data class CriarAgendamentoRequest(
    val hora: String,
    val diasSemana: Short,
    val ativo: Boolean = true,
)

@Serializable
data class AtualizarAgendamentoRequest(
    val hora: String? = null,
    val diasSemana: Short? = null,
    val ativo: Boolean? = null,
)

@Serializable
data class AgendamentoResponse(
    val id: String,
    val dispositivoId: String,
    val hora: String,
    val diasSemana: Short,
    val ativo: Boolean,
    val criadoEm: String,
    val atualizadoEm: String,
)
