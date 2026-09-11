package br.com.tavaressan.cafey.shared.ui.care

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.tavaressan.cafey.shared.LocalAppContainer
import br.com.tavaressan.cafey.shared.domain.model.StatusDescalcificacaoResponse
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme

/**
 * UC-16/17 — alerta de descalcificação e baixa do contador (APP-07). Espelha
 * `docs/docs_interface/prototype/care.html` no cartão "Descalcificar" (o único com dado real);
 * "Enxaguar o circuito" e "Trocar o filtro de água" não têm contador nem endpoint no backend hoje
 * — omitidos em vez de inventados.
 */
@Composable
fun CareScreen() {
    val container = LocalAppContainer.current
    val viewModel = viewModel<CareViewModel>(
        factory = viewModelFactory { initializer { CareViewModel(container.deviceApi, container.eventApi) } },
    )
    val state by viewModel.uiState.collectAsState()

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

        val status = state.status
        if (status == null) {
            Text(
                "Nenhum dispositivo vinculado ainda.",
                style = CafeyTheme.typography.body,
                color = CafeyTheme.colors.muted,
                modifier = Modifier.padding(top = 24.dp),
            )
        } else {
            DescalcificacaoCard(
                status = status,
                podeDarBaixa = state.podeDarBaixa,
                baixaInFlight = state.baixaInFlight,
                onDarBaixa = viewModel::darBaixa,
            )
        }
    }
}

@Composable
private fun DescalcificacaoCard(
    status: StatusDescalcificacaoResponse,
    podeDarBaixa: Boolean,
    baixaInFlight: Boolean,
    onDarBaixa: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .background(
                if (status.precisaDescalcificar) CafeyTheme.colors.brandTint else CafeyTheme.colors.surface,
                CafeyTheme.shapes.large,
            )
            .border(1.dp, CafeyTheme.colors.line, CafeyTheme.shapes.large)
            .padding(20.dp),
    ) {
        Text(
            // UC-16 — alerta quando o contador atinge o limiar do dispositivo.
            if (status.precisaDescalcificar) "Hora de descalcificar" else "Descalcificação em dia",
            style = CafeyTheme.typography.cardTitle,
            color = CafeyTheme.colors.ink,
        )
        LinearProgressIndicator(
            progress = { (status.percentualUso / 100.0).toFloat().coerceIn(0f, 1f) },
            color = if (status.precisaDescalcificar) CafeyTheme.colors.brand else CafeyTheme.colors.blue,
            trackColor = CafeyTheme.colors.sunken,
            modifier = Modifier.fillMaxWidth().height(8.dp).padding(top = 14.dp),
        )
        Text(
            "${status.contadorPreparos} de ${status.limiarDescalcificacao} preparos",
            style = CafeyTheme.typography.bodySmall,
            color = CafeyTheme.colors.muted,
            modifier = Modifier.padding(top = 8.dp),
        )

        if (podeDarBaixa) {
            Button(
                onClick = onDarBaixa,
                enabled = !baixaInFlight,
                colors = ButtonDefaults.buttonColors(containerColor = CafeyTheme.colors.brand),
                shape = CafeyTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                if (baixaInFlight) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp), color = CafeyTheme.colors.brandOn)
                } else {
                    // UC-17 — dar baixa zera o contador no backend.
                    Text("Já fiz isso")
                }
            }
        } else {
            Text(
                "Só o proprietário do dispositivo pode registrar a descalcificação.",
                style = CafeyTheme.typography.caption,
                color = CafeyTheme.colors.dim,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
