package br.com.tavaressan.cafey.shared.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.tavaressan.cafey.shared.LocalAppContainer
import br.com.tavaressan.cafey.shared.domain.model.BarraOrigem
import br.com.tavaressan.cafey.shared.domain.model.EstatisticasConsumoResponse
import br.com.tavaressan.cafey.shared.domain.model.EventoResponse
import br.com.tavaressan.cafey.shared.domain.model.barrasPorOrigem
import br.com.tavaressan.cafey.shared.domain.model.duracaoFormatada
import br.com.tavaressan.cafey.shared.domain.model.origemLabel
import br.com.tavaressan.cafey.shared.domain.model.resultadoLabel
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme

private val DIAS_SEMANA_LABEL = listOf("S", "T", "Q", "Q", "S", "S", "D")
private val DIAS_SEMANA_NOME = listOf("Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo")

/**
 * UC-14/15 — histórico de preparos, estatísticas de consumo (APP-06), distribuição por origem
 * (APP-10) e ritmo semanal por dia da semana (APP-31). A tela inteira rola com o histórico aberto.
 */
@Composable
fun HistoryScreen() {
    val container = LocalAppContainer.current
    val viewModel = viewModel<HistoryViewModel>(
        factory = viewModelFactory {
            initializer {
                HistoryViewModel(
                    container.deviceApi,
                    container.eventApi,
                    container.bleClient,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(CafeyTheme.colors.ground).padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(Modifier.height(12.dp)) }
        item { Text("Seu ritmo", style = CafeyTheme.typography.screenTitle, color = CafeyTheme.colors.ink) }

        if (state.loading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CafeyTheme.colors.brand)
                }
            }
        } else {
            state.errorMessage?.let { msg ->
                item {
                    Text(
                        msg,
                        color = CafeyTheme.colors.brandDeep,
                        style = CafeyTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            state.estatisticas?.let { estatisticas ->
                item { EstatisticasCard(estatisticas) }
            }

            item {
                HistoricoHeader(
                    total = state.estatisticas?.totalPreparosConcluidos ?: 0,
                    expandido = state.historicoExpandido,
                    onToggle = viewModel::alternarHistorico,
                )
            }

            if (state.historicoExpandido) {
                if (state.loadingEventos && state.eventos.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = CafeyTheme.colors.brand)
                        }
                    }
                } else if (state.eventos.isEmpty()) {
                    item {
                        Text(
                            "Nenhum preparo registrado ainda.",
                            style = CafeyTheme.typography.body,
                            color = CafeyTheme.colors.muted,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                } else {
                    items(state.eventos, key = { it.id }) { evento -> EventoRow(evento) }
                    item {
                        if (state.hasMore) {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                                if (state.loadingMore) {
                                    CircularProgressIndicator(modifier = Modifier.padding(4.dp), color = CafeyTheme.colors.brand)
                                } else {
                                    Text(
                                        "Carregar mais",
                                        style = CafeyTheme.typography.bodySmall,
                                        color = CafeyTheme.colors.blueDeep,
                                        modifier = Modifier.padding(4.dp),
                                    )
                                }
                            }
                            // Carrega a próxima página assim que este sentinela aparece, uma vez por
                            // entrada em composição (chave Unit). loadMore() ignora chamadas enquanto
                            // o histórico não estiver aberto/carregado.
                            LaunchedEffect(Unit) { viewModel.loadMore() }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun EstatisticasCard(estatisticas: EstatisticasConsumoResponse) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .background(CafeyTheme.colors.surface, CafeyTheme.shapes.large)
            .border(1.dp, CafeyTheme.colors.line, CafeyTheme.shapes.large)
            .padding(18.dp),
    ) {
        Text("${estatisticas.totalPreparosConcluidos} preparos concluídos", style = CafeyTheme.typography.cardHero, color = CafeyTheme.colors.ink)
        val horas = estatisticas.tempoTotalPreparoSegundos / 3600
        val minutos = (estatisticas.tempoTotalPreparoSegundos % 3600) / 60
        Text(
            "Tempo total de preparo: ${horas}h${minutos.toString().padStart(2, '0')}",
            style = CafeyTheme.typography.bodySmall,
            color = CafeyTheme.colors.muted,
            modifier = Modifier.padding(top = 6.dp),
        )

        RitmoSemanalSection(estatisticas.porDiaDaSemana, modifier = Modifier.padding(top = 14.dp))

        val barras = estatisticas.barrasPorOrigem()
        if (barras.isNotEmpty()) {
            OrigemBarChart(barras, modifier = Modifier.padding(top = 14.dp))
        }
    }
}

@Composable
private fun RitmoSemanalSection(porDiaDaSemana: List<Int>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "Ritmo semanal",
            style = CafeyTheme.typography.caption,
            color = CafeyTheme.colors.ink3,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (porDiaDaSemana.size == 7) {
            RitmoSemanalChart(porDiaDaSemana)
        } else {
            val mensagem = "Ainda não há dados suficientes para mostrar o ritmo semanal."
            Text(mensagem, style = CafeyTheme.typography.bodySmall, color = CafeyTheme.colors.muted)
        }
    }
}

@Composable
private fun RitmoSemanalChart(porDiaDaSemana: List<Int>) {
    val maximo = porDiaDaSemana.maxOrNull() ?: 0
    val chartHeight = 96.dp
    Row(
        modifier = Modifier.fillMaxWidth().height(chartHeight + 28.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        porDiaDaSemana.forEachIndexed { index, valor ->
            val destaque = maximo > 0 && valor == maximo
            val fracao = if (maximo > 0) valor.toFloat() / maximo.toFloat() else 0f
            val barraAltura = if (fracao > 0f) (chartHeight * fracao).coerceAtLeast(6.dp) else 4.dp
            val descricao = "${DIAS_SEMANA_NOME[index]}: $valor ${if (valor == 1) "preparo" else "preparos"}"
            Column(
                modifier = Modifier.weight(1f).semantics { contentDescription = descricao },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .height(chartHeight)
                        .width(18.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(barraAltura)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (destaque) CafeyTheme.colors.brand else CafeyTheme.colors.sunken),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    DIAS_SEMANA_LABEL[index],
                    style = CafeyTheme.typography.caption,
                    color = if (destaque) CafeyTheme.colors.brandDeep else CafeyTheme.colors.muted,
                )
            }
        }
    }
}

@Composable
private fun HistoricoHeader(total: Long, expandido: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "▼",
            modifier = Modifier.rotate(if (expandido) 0f else -90f),
            style = CafeyTheme.typography.body,
            color = CafeyTheme.colors.ink3,
        )
        Text(
            "Histórico de preparos · $total",
            style = CafeyTheme.typography.cardTitle,
            color = CafeyTheme.colors.ink,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** Gráfico de barras da distribuição de preparos por origem (UC-15 / APP-10). Reaproveita o
 * `LinearProgressIndicator` já usado em `CareScreen` para o indicador de descalcificação, em vez
 * de desenhar barras do zero — mesmo componente e mesmos tokens de cor (`brand`/`sunken`). */
@Composable
private fun OrigemBarChart(barras: List<BarraOrigem>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        barras.forEach { barra ->
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    origemLabel(barra.origem),
                    style = CafeyTheme.typography.caption,
                    color = CafeyTheme.colors.ink3,
                    modifier = Modifier.width(96.dp),
                )
                LinearProgressIndicator(
                    progress = { barra.fracao },
                    color = CafeyTheme.colors.brand,
                    trackColor = CafeyTheme.colors.sunken,
                    modifier = Modifier.weight(1f).height(8.dp),
                )
                Text(
                    barra.total.toString(),
                    style = CafeyTheme.typography.caption,
                    color = CafeyTheme.colors.muted,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun EventoRow(evento: EventoResponse) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CafeyTheme.colors.surface, CafeyTheme.shapes.medium)
            .border(1.dp, CafeyTheme.colors.line, CafeyTheme.shapes.medium)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(evento.timestamp.replace("T", " ").substringBeforeLast(":"), style = CafeyTheme.typography.body, color = CafeyTheme.colors.ink)
            Text(evento.origemLabel(), style = CafeyTheme.typography.caption, color = CafeyTheme.colors.muted)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(evento.resultadoLabel(), style = CafeyTheme.typography.body, color = CafeyTheme.colors.ink2)
            Text(evento.duracaoFormatada(), style = CafeyTheme.typography.mono, color = CafeyTheme.colors.muted)
        }
    }
}
