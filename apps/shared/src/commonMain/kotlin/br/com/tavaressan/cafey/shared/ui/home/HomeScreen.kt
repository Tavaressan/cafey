package br.com.tavaressan.cafey.shared.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.tavaressan.cafey.shared.LocalAppContainer
import br.com.tavaressan.cafey.shared.domain.model.DeviceState
import br.com.tavaressan.cafey.shared.ui.theme.CafeyStar
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme

/**
 * UC-06/07/08/09 — operação e estado em tempo real. Espelha
 * `docs/docs_interface/prototype/home.html` (layout mobile: `.shell`, `.stage-card`, `.card`,
 * `.duo`). Simplificações conscientes por escopo:
 * - Sem o anel decorativo pontilhado do mostrador (puramente estético, `.stage__ring`).
 * - Sem o cartão "Próximo preparo" com dado real — agendamentos são APP-05, ainda não implementado.
 * - "Sequência de manhãs" (streak) não tem endpoint no backend hoje; omitido em vez de inventado.
 */
@Composable
fun HomeScreen(onGoToDeviceRegister: () -> Unit = {}) {
    val container = LocalAppContainer.current
    val viewModel = viewModel<HomeViewModel>(
        factory = viewModelFactory { initializer { HomeViewModel(container.deviceApi, container.commandApi) } },
    )
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CafeyTheme.colors.ground)
            .padding(22.dp),
    ) {
        if (state.loading) {
            DeviceHeader(name = "Caféy", online = false)
            Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CafeyTheme.colors.brand)
            }
            return@Column
        }

        state.errorMessage?.let { ErrorBanner(it) }

        // APP-13 — conta nova sem dispositivo: caminho explícito para cadastrar, em vez de
        // deixar a tela de operação sem sentido (sem dispositivo, não há o que operar).
        if (state.device == null) {
            EmptyDeviceState(onRegister = onGoToDeviceRegister)
            return@Column
        }

        DeviceHeader(name = state.device?.nome ?: "Caféy", online = state.device?.online ?: false)

        StageCard(
            deviceState = state.deviceState,
            commandInFlight = state.commandInFlight,
            onPrepare = viewModel::ligar,
            onCancel = viewModel::cancelar,
            onTurnOff = viewModel::desligar,
        )
    }
}

@Composable
private fun DeviceHeader(name: String, online: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
    ) {
        CafeyStar(color = CafeyTheme.colors.brand, size = 28.dp)
        Column(modifier = Modifier.padding(start = 11.dp)) {
            Text(name, style = CafeyTheme.typography.cardTitle, color = CafeyTheme.colors.ink)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (online) CafeyTheme.colors.blue else CafeyTheme.colors.dim),
                )
                Text(
                    text = if (online) "Online" else "Offline",
                    style = CafeyTheme.typography.caption,
                    color = CafeyTheme.colors.muted,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

/** APP-13 — estado vazio: conta nova ainda sem dispositivo cadastrado. */
@Composable
private fun EmptyDeviceState(onRegister: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp)
            .background(CafeyTheme.colors.surface, CafeyTheme.shapes.large)
            .border(1.dp, CafeyTheme.colors.line, CafeyTheme.shapes.large)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CafeyStar(color = CafeyTheme.colors.muted, size = 40.dp)
        Text(
            "Nenhum dispositivo cadastrado",
            style = CafeyTheme.typography.cardTitle,
            color = CafeyTheme.colors.ink,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            "Cadastre sua cafeteira para começar a usar o Caféy.",
            style = CafeyTheme.typography.bodySmall,
            color = CafeyTheme.colors.muted,
            modifier = Modifier.padding(top = 6.dp),
        )
        Button(
            onClick = onRegister,
            colors = ButtonDefaults.buttonColors(containerColor = CafeyTheme.colors.brand),
            shape = CafeyTheme.shapes.small,
            contentPadding = PaddingValues(vertical = 16.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        ) {
            Text("Cadastrar dispositivo", style = CafeyTheme.typography.buttonLabel, color = CafeyTheme.colors.brandOn)
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .background(CafeyTheme.colors.brandTint, CafeyTheme.shapes.medium)
            .padding(16.dp),
    ) {
        Text(message, color = CafeyTheme.colors.brandDeep, style = CafeyTheme.typography.bodySmall)
    }
}

@Composable
private fun StageCard(
    deviceState: DeviceState,
    commandInFlight: Boolean,
    onPrepare: () -> Unit,
    onCancel: () -> Unit,
    onTurnOff: () -> Unit,
) {
    val (stateLabel, starColor) = when (deviceState) {
        DeviceState.Idle -> "Pronta" to CafeyTheme.colors.blue
        DeviceState.Brewing -> "Preparando" to CafeyTheme.colors.brand
        DeviceState.Error -> "Erro" to CafeyTheme.colors.brandDeep
        is DeviceState.Unknown -> deviceState.raw.ifBlank { "Sem dados" } to CafeyTheme.colors.muted
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CafeyTheme.colors.surface, CafeyTheme.shapes.large)
            .border(1.dp, CafeyTheme.colors.line, CafeyTheme.shapes.large)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(176.dp)
                .clip(CircleShape)
                .background(CafeyTheme.colors.surface)
                .border(1.dp, CafeyTheme.colors.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CafeyStar(color = starColor, size = 44.dp)
                Text(
                    stateLabel,
                    style = CafeyTheme.typography.cardTitle,
                    color = CafeyTheme.colors.ink,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (deviceState == DeviceState.Brewing) {
                Button(
                    onClick = onCancel,
                    enabled = !commandInFlight,
                    colors = ButtonDefaults.buttonColors(containerColor = CafeyTheme.colors.brand),
                    shape = CafeyTheme.shapes.small,
                    contentPadding = PaddingValues(vertical = 16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cancelar preparo", style = CafeyTheme.typography.buttonLabel, color = CafeyTheme.colors.brandOn)
                }
            } else {
                Button(
                    onClick = onPrepare,
                    enabled = !commandInFlight,
                    colors = ButtonDefaults.buttonColors(containerColor = CafeyTheme.colors.brand),
                    shape = CafeyTheme.shapes.small,
                    contentPadding = PaddingValues(vertical = 16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Preparar agora", style = CafeyTheme.typography.buttonLabel, color = CafeyTheme.colors.brandOn)
                }
                OutlinedButton(
                    onClick = onTurnOff,
                    enabled = !commandInFlight,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CafeyTheme.colors.ink3),
                    shape = CafeyTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                ) {
                    Text("Desligar", style = CafeyTheme.typography.body)
                }
            }
        }
    }
}
