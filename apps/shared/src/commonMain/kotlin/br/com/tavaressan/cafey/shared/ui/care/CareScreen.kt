package br.com.tavaressan.cafey.shared.ui.care

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.tavaressan.cafey.shared.LocalAppContainer
import br.com.tavaressan.cafey.shared.domain.model.CuidadosResponse
import br.com.tavaressan.cafey.shared.domain.model.TipoCuidado
import br.com.tavaressan.cafey.shared.ui.LocalNavShellSizeClass
import br.com.tavaressan.cafey.shared.ui.NavShellSizeClass
import br.com.tavaressan.cafey.shared.ui.theme.CafeyStar
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme

/**
 * UC-16/17 — alertas e baixa dos cuidados (APP-07). Espelha
 * `docs/docs_interface/prototype/care.html`: "Enxaguar o circuito" (a cada 40 preparos), "Trocar o
 * filtro de água" (a cada 300) e "Descalcificar" (limiar do dispositivo), todos com dado real do
 * backend (issue #190). O cuidado com a maior fração contador/limiar, decidido pelo backend, vira o
 * cartão de destaque no topo; os outros dois ficam abaixo (lado a lado a partir do tablet).
 *
 * Fora do escopo, sem endpoint/comando correspondente: "Iniciar enxágue" (disparar o ciclo pela
 * máquina), "Lembrar aos 280" (lembrete do filtro), o gauge circular e as previsões de data
 * ("no seu ritmo") do protótipo.
 */
@Composable
fun CareScreen() {
    val container = LocalAppContainer.current
    val viewModel = viewModel<CareViewModel>(
        factory = viewModelFactory { initializer { CareViewModel(container.deviceApi, container.eventApi) } },
    )
    val state by viewModel.uiState.collectAsState()

    CareContent(state = state, sizeClass = LocalNavShellSizeClass.current, onDarBaixa = viewModel::darBaixa)
}

/** Corpo visual da tela, separado de [CareScreen] para ser testável sem `AppContainer`/rede. */
@Composable
internal fun CareContent(
    state: CareUiState,
    sizeClass: NavShellSizeClass,
    onDarBaixa: (TipoCuidado) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(CafeyTheme.colors.ground).padding(22.dp)) {
        Text("Cuidados", style = CafeyTheme.typography.screenTitle, color = CafeyTheme.colors.ink)

        if (state.loading) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CafeyTheme.colors.brand)
            }
            return@Column
        }

        state.errorMessage?.let {
            Text(it, color = CafeyTheme.colors.brandDeep, style = CafeyTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
        }

        val cuidados = state.cuidados
        if (cuidados == null) {
            Text(
                "Nenhum dispositivo vinculado ainda.",
                style = CafeyTheme.typography.body,
                color = CafeyTheme.colors.muted,
                modifier = Modifier.padding(top = 24.dp),
            )
        } else {
            val destaque = cuidados.destaque
            val secundarios = TipoCuidado.entries.filter { it != destaque }

            @Composable
            fun card(tipo: TipoCuidado) = CuidadoCard(
                cuidado = cuidados.toView(tipo),
                destaque = tipo == destaque,
                podeDarBaixa = state.podeDarBaixa,
                baixaInFlight = state.baixaInFlight,
                onDarBaixa = { onDarBaixa(tipo) },
            )

            card(destaque)
            // Tablet+ (≥768.dp, `.wide` de cafey.css): cards secundários lado a lado (grid 1fr/1fr)
            // — issue #188.
            if (sizeClass == NavShellSizeClass.Compact) {
                secundarios.forEach { card(it) }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    secundarios.forEach { Box(modifier = Modifier.weight(1f)) { card(it) } }
                }
            }
        }
    }
}

/** Texto e números de um cartão, já resolvidos a partir de [CuidadosResponse]. */
private class CuidadoView(
    val tipo: TipoCuidado,
    val titulo: String,
    val tituloAlerta: String,
    val meta: String,
    val descricao: String?,
    val percentualUso: Double,
    val precisaAtencao: Boolean,
    val acaoNegada: String,
)

private fun CuidadosResponse.toView(tipo: TipoCuidado): CuidadoView = when (tipo) {
    TipoCuidado.ENXAGUE -> CuidadoView(
        tipo = tipo,
        titulo = "Enxaguar o circuito",
        tituloAlerta = "Hora de enxaguar o circuito",
        meta = "${enxague.contadorPreparos} de ${enxague.limiarPreparos} preparos desde o último enxágue",
        descricao = "Passe uma jarra de água pura, sem café no cesto. Leva uns quatro minutos e tira o óleo velho da linha.",
        percentualUso = enxague.percentualUso,
        precisaAtencao = enxague.precisaAtencao,
        acaoNegada = "Só o proprietário do dispositivo pode registrar o enxágue.",
    )
    TipoCuidado.FILTRO -> CuidadoView(
        tipo = tipo,
        titulo = "Trocar o filtro de água",
        tituloAlerta = "Hora de trocar o filtro de água",
        meta = "${filtro.contadorPreparos} de ${filtro.limiarPreparos} preparos",
        descricao = null,
        percentualUso = filtro.percentualUso,
        precisaAtencao = filtro.precisaAtencao,
        acaoNegada = "Só o proprietário do dispositivo pode registrar a troca do filtro.",
    )
    TipoCuidado.DESCALCIFICACAO -> CuidadoView(
        tipo = tipo,
        titulo = "Descalcificar",
        // UC-16 — alerta quando o contador atinge o limiar do dispositivo.
        tituloAlerta = "Hora de descalcificar",
        meta = "${descalcificacao.contadorPreparos} de ${descalcificacao.limiarDescalcificacao} preparos",
        descricao = null,
        percentualUso = descalcificacao.percentualUso,
        precisaAtencao = descalcificacao.precisaDescalcificar,
        acaoNegada = "Só o proprietário do dispositivo pode registrar a descalcificação.",
    )
}

@Composable
private fun CuidadoCard(
    cuidado: CuidadoView,
    destaque: Boolean,
    podeDarBaixa: Boolean,
    baixaInFlight: TipoCuidado?,
    onDarBaixa: () -> Unit,
) {
    val emAlerta = destaque || cuidado.precisaAtencao
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .testTag("cuidado-${cuidado.tipo.name}")
            .background(
                // `card--accent` do protótipo: o destaque (e qualquer cuidado vencido) ganha o tint da marca.
                if (emAlerta) CafeyTheme.colors.brandTint else CafeyTheme.colors.surface,
                CafeyTheme.shapes.large,
            )
            .border(1.dp, CafeyTheme.colors.line, CafeyTheme.shapes.large)
            .padding(20.dp),
    ) {
        if (destaque && !cuidado.precisaAtencao) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                CafeyStar(color = CafeyTheme.colors.brand, size = 14.dp)
                Text(
                    "Chegando",
                    style = CafeyTheme.typography.caption,
                    color = CafeyTheme.colors.brandDeep,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        Text(
            if (cuidado.precisaAtencao) cuidado.tituloAlerta else cuidado.titulo,
            style = CafeyTheme.typography.cardTitle,
            color = CafeyTheme.colors.ink,
        )
        LinearProgressIndicator(
            progress = { (cuidado.percentualUso / 100.0).toFloat().coerceIn(0f, 1f) },
            color = if (emAlerta) CafeyTheme.colors.brand else CafeyTheme.colors.blue,
            trackColor = CafeyTheme.colors.sunken,
            modifier = Modifier.fillMaxWidth().height(8.dp).padding(top = 14.dp),
        )
        Text(
            cuidado.meta,
            style = CafeyTheme.typography.bodySmall,
            color = CafeyTheme.colors.muted,
            modifier = Modifier.padding(top = 8.dp),
        )
        cuidado.descricao?.let {
            Text(it, style = CafeyTheme.typography.body, color = CafeyTheme.colors.ink2, modifier = Modifier.padding(top = 12.dp))
        }

        if (podeDarBaixa) {
            Button(
                onClick = onDarBaixa,
                enabled = baixaInFlight == null,
                colors = ButtonDefaults.buttonColors(containerColor = CafeyTheme.colors.brand),
                shape = CafeyTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                if (baixaInFlight == cuidado.tipo) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp), color = CafeyTheme.colors.brandOn)
                } else {
                    // UC-17 — dar baixa zera o contador do cuidado no backend.
                    Text("Já fiz isso")
                }
            }
        } else {
            Text(
                cuidado.acaoNegada,
                style = CafeyTheme.typography.caption,
                color = CafeyTheme.colors.dim,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
