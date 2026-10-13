package br.com.tavaressan.cafey.shared.domain.model

import kotlinx.serialization.Serializable

/** Espelha `br.com.tavaressan.cafey.schedule.ScheduleDto`. `diasSemana` é uma máscara de bits
 * (1–127): bit 0 = domingo … bit 6 = sábado, igual ao `CriarAgendamentoRequest` do backend.
 * `duracaoPreparoS` (segundos) é opcional: `null` = o backend usa a duração do dispositivo. */
@Serializable
data class CriarAgendamentoRequest(
    val hora: String,
    val diasSemana: Short,
    val ativo: Boolean = true,
    val duracaoPreparoS: Int? = null,
)

@Serializable
data class AtualizarAgendamentoRequest(
    val hora: String? = null,
    val diasSemana: Short? = null,
    val ativo: Boolean? = null,
    val duracaoPreparoS: Int? = null,
)

@Serializable
data class AgendamentoResponse(
    val id: String,
    val dispositivoId: String,
    val hora: String,
    val diasSemana: Short,
    val ativo: Boolean,
    val duracaoPreparoS: Int? = null,
    val criadoEm: String,
    val atualizadoEm: String,
)

/** Opções "Desliga sozinha após" (4/6/8/10 min), em segundos — os únicos valores que o backend
 * aceita (`DURACOES_PREPARO_PERMITIDAS_S`); qualquer outro vira HTTP 400. */
val DURACOES_PREPARO_S = listOf(240, 360, 480, 600)

/** Opção pré-selecionada ao criar um agendamento (8 min, igual ao protótipo). */
const val DURACAO_PREPARO_PADRAO_S = 480

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
