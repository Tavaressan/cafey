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

/** Rótulo legível de `resultado` para a lista de histórico (UC-14). Cai no valor bruto para
 * qualquer coisa que o backend venha a mandar além dos três valores documentados. */
fun EventoResponse.resultadoLabel(): String = when (resultado.uppercase()) {
    "CONCLUIDO" -> "Concluído"
    "CANCELADO" -> "Cancelado"
    "ERRO" -> "Erro"
    else -> resultado
}

/** Rótulo legível de uma origem bruta (`APP`, `AGENDAMENTO`, `BOTAO`), compartilhado entre a lista
 * de histórico (UC-14) e o gráfico de distribuição por origem (UC-15 / APP-10). */
fun origemLabel(origem: String): String = when (origem.uppercase()) {
    "APP" -> "App"
    "AGENDAMENTO" -> "Agendamento"
    "BOTAO" -> "Botão"
    else -> origem
}

/** Rótulo legível de `origem` para a lista de histórico (UC-14). */
fun EventoResponse.origemLabel(): String = origemLabel(origem)

/** Duração do preparo no formato `m:ss`, para a lista de histórico (UC-14). */
fun EventoResponse.duracaoFormatada(): String {
    val minutos = duracaoS / 60
    val segundos = duracaoS % 60
    return "$minutos:${segundos.toString().padStart(2, '0')}"
}

@Serializable
data class EstatisticasConsumoResponse(
    val totalPreparosConcluidos: Long,
    val porOrigem: Map<String, Long>,
    val tempoTotalPreparoSegundos: Long,
)

/** Uma barra do gráfico de distribuição por origem (UC-15 / APP-10). `fracao` é o total
 * normalizado pelo maior valor do grupo, em `[0, 1]`, pronto para dimensionar a barra na UI. */
data class BarraOrigem(
    val origem: String,
    val total: Long,
    val fracao: Float,
)

/** Distribuição por origem ordenada da maior para a menor, para o gráfico de barras da tela de
 * histórico (UC-15 / APP-10). Vazio quando não há preparos registrados. */
fun EstatisticasConsumoResponse.barrasPorOrigem(): List<BarraOrigem> {
    val maximo = porOrigem.values.maxOrNull() ?: return emptyList()
    if (maximo == 0L) return emptyList()
    return porOrigem.entries
        .sortedByDescending { it.value }
        .map { (origem, total) -> BarraOrigem(origem, total, total.toFloat() / maximo.toFloat()) }
}

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
