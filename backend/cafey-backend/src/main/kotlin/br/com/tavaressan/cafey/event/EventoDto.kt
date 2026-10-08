package br.com.tavaressan.cafey.event

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class IngestaoEventoRequest(
    @field:NotBlank(message = "ID do evento é obrigatório")
    val eventoId: String,

    val tipo: String = "PREPARO",

    @field:NotBlank(message = "Resultado é obrigatório (CONCLUIDO, CANCELADO, ERRO)")
    val resultado: String,

    @field:NotBlank(message = "Origem é obrigatória (APP, AGENDAMENTO, BOTAO)")
    val origem: String,

    val duracaoS: Int = 0,

    @field:NotNull(message = "Timestamp é obrigatório")
    val timestamp: Instant,

    val detalheErro: String? = null
)

data class EventoResponse(
    val id: UUID,
    val eventoId: String,
    val tipo: String,
    val resultado: String,
    val origem: String,
    val duracaoS: Int,
    val timestamp: Instant,
    val detalheErro: String?,
    val criadoEm: Instant
)

data class EstatisticasConsumoResponse(
    val totalPreparosConcluidos: Long,
    val porOrigem: Map<String, Long>,
    val tempoTotalPreparoSegundos: Long,
    val sequenciaManhasDias: Int
)

data class StatusDescalcificacaoResponse(
    val contadorPreparos: Int,
    val limiarDescalcificacao: Int,
    val precisaDescalcificar: Boolean,
    val percentualUso: Double
)

/** Issue #190 — estado de um cuidado contado em preparos desde a última baixa (enxágue, filtro). */
data class StatusCuidadoResponse(
    val contadorPreparos: Int,
    val limiarPreparos: Int,
    val precisaAtencao: Boolean,
    val percentualUso: Double
)

/** Issue #190 — os três cuidados da tela Cuidados e qual deles é o destaque. */
data class CuidadosResponse(
    val descalcificacao: StatusDescalcificacaoResponse,
    val enxague: StatusCuidadoResponse,
    val filtro: StatusCuidadoResponse,
    val destaque: TipoCuidado
)

data class ProxyBleEventosRequest(
    val eventos: List<IngestaoEventoRequest>
)
