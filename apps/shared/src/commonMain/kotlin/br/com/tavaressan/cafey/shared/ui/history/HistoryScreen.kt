package br.com.tavaressan.cafey.shared.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.tavaressan.cafey.shared.LocalAppContainer
import br.com.tavaressan.cafey.shared.domain.model.EstatisticasConsumoResponse
import br.com.tavaressan.cafey.shared.domain.model.EventoResponse
import br.com.tavaressan.cafey.shared.domain.model.duracaoFormatada
import br.com.tavaressan.cafey.shared.domain.model.origemLabel
import br.com.tavaressan.cafey.shared.domain.model.resultadoLabel
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme

/**
 * UC-14/15 — histórico de preparos em lista e estatísticas de consumo (APP-06). Espelha
 * `docs/docs_interface/prototype/rhythm.html` no essencial (resumo + lista), simplificado por
 * escopo:
 * - Os gráficos de distribuição por horário e a "sequência de manhãs" (streak) do protótipo não
 *   têm endpoint correspondente — só `EstatisticasConsumoResponse` (totais e por origem) e a lista
 *   paginada de eventos existem hoje. Omitidos em vez de inventados; ficam para APP-10 (gráficos).
 */
@Composable
fun HistoryScreen() {
    val container = LocalAppContainer.current
    val viewModel = viewModel<HistoryViewModel>(
        factory = viewModelFactory { initializer { HistoryViewModel(container.deviceApi, container.eventApi) } },
    )
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(CafeyTheme.colors.ground).padding(22.dp)) {
        Text("Seu ritmo", style = CafeyTheme.typography.screenTitle, color = CafeyTheme.colors.ink)

        if (state.loading) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CafeyTheme.colors.brand)
            }
            return@Column
        }

        state.errorMessage?.let {
            Text(it, color = CafeyTheme.colors.brandDeep, style = CafeyTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
        }

        state.estatisticas?.let { EstatisticasCard(it) }

        Text("Histórico de preparos", style = CafeyTheme.typography.cardTitle, color = CafeyTheme.colors.ink, modifier = Modifier.padding(top = 20.dp))

        if (state.eventos.isEmpty()) {
            Text(
                "Nenhum preparo registrado ainda.",
                style = CafeyTheme.typography.body,
                color = CafeyTheme.colors.muted,
                modifier = Modifier.padding(top = 12.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        // Carrega a próxima página assim que este item aparece — simples "load more"
                        // sem depender de um listener de scroll dedicado (fora de escopo aqui).
                        // A chave é Unit, e não state.page: loadMore() avança page, então usar page
                        // como chave relançava o efeito a cada sucesso e encadeava todas as páginas
                        // de uma vez enquanto o sentinela seguisse composto. Com Unit, dispara uma
                        // vez por entrada em composição — a LazyColumn recompõe o item ao reaparecer.
                        LaunchedEffect(Unit) { viewModel.loadMore() }
                    }
                }
            }
        }
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
        if (estatisticas.porOrigem.isNotEmpty()) {
            Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                estatisticas.porOrigem.forEach { (origem, total) ->
                    Text("$origem: $total", style = CafeyTheme.typography.caption, color = CafeyTheme.colors.ink3)
                }
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
