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

/** Rótulos curtos dos 7 dias, na mesma ordem da máscara de bits (índice 0 = domingo). */
val DIAS_SEMANA_LABELS = listOf("D", "S", "T", "Q", "Q", "S", "S")

/** Lê o bit do dia `indice` (0 = domingo … 6 = sábado) da máscara `diasSemana`. */
fun Short.diaAtivo(indice: Int): Boolean = (this.toInt() shr indice) and 1 == 1

/** Codifica a lista de 7 booleanos (índice 0 = domingo … 6 = sábado) na máscara de bits do backend. */
fun diasSemanaMask(diasAtivos: List<Boolean>): Short {
    require(diasAtivos.size == 7) { "diasAtivos deve ter exatamente 7 posições (domingo a sábado)" }
    return diasAtivos.foldIndexed(0) { indice, acc, ativo ->
        if (ativo) acc or (1 shl indice) else acc
    }.toShort()
}
