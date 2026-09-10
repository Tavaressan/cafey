package br.com.tavaressan.cafey.shared.domain.model

import kotlinx.serialization.Serializable

/** Espelha `br.com.tavaressan.cafey.event.EventoDto`. */
@Serializable
data class EventoResponse(
    val id: String,
    val eventoId: String,
    val tipo: String,
    val resultado: String,
    val origem: String,
    val duracaoS: Int,
    val timestamp: String,
    val detalheErro: String? = null,
    val criadoEm: String,
)

@Serializable
data class EstatisticasConsumoResponse(
    val totalPreparosConcluidos: Long,
    val porOrigem: Map<String, Long>,
    val tempoTotalPreparoSegundos: Long,
)

@Serializable
data class StatusDescalcificacaoResponse(
    val contadorPreparos: Int,
    val limiarDescalcificacao: Int,
    val precisaDescalcificar: Boolean,
    val percentualUso: Double,
)

/** Página do Spring Data (`org.springframework.data.domain.Page<T>`) — só os campos que a UI usa. */
@Serializable
data class PageResponse<T>(
    val content: List<T>,
    val totalElements: Long,
    val totalPages: Int,
    val number: Int,
    val size: Int,
    val last: Boolean,
)
