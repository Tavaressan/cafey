package br.com.tavaressan.cafey.shared.ui.base

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import br.com.tavaressan.cafey.shared.domain.model.DispositivoResponse
import br.com.tavaressan.cafey.shared.ui.theme.CafeyStar
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme

@Composable
fun BaseScreen() {
    val container = LocalAppContainer.current
    val viewModel = viewModel<BaseViewModel>(
        factory = viewModelFactory { initializer { BaseViewModel(container.deviceApi, container.themePreference) } },
    )
    val state by viewModel.uiState.collectAsState()

    BaseContent(
        loading = state.loading,
        device = state.device,
        darkTheme = state.darkTheme,
        errorMessage = state.errorMessage,
        onToggleTheme = viewModel::toggleTheme,
    )
}

/**
 * Corpo visual da tela "Base", separado de [BaseScreen] para ser testável por composição sem
 * `AppContainer`/rede (mesmo padrão de `HomeContentLayout`/`ScheduleContentLayout`, issue #188).
 */
@Composable
internal fun BaseContent(
    loading: Boolean,
    device: DispositivoResponse?,
    darkTheme: Boolean,
    errorMessage: String?,
    onToggleTheme: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(CafeyTheme.colors.ground).padding(22.dp)) {
        Text("Base da cozinha", style = CafeyTheme.typography.screenTitle, color = CafeyTheme.colors.ink)
        Text(
            "O módulo debaixo da sua cafeteira.",
            style = CafeyTheme.typography.bodySmall,
            color = CafeyTheme.colors.muted,
            modifier = Modifier.padding(top = 4.dp),
        )

        if (loading) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CafeyTheme.colors.brand)
            }
            return@Column
        }

        errorMessage?.let {
            Text(it, color = CafeyTheme.colors.brandDeep, style = CafeyTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
        }

        if (device == null) {
            Text(
                "Nenhum dispositivo vinculado ainda.",
                style = CafeyTheme.typography.body,
                color = CafeyTheme.colors.muted,
                modifier = Modifier.padding(top = 24.dp),
            )
        } else {
            DeviceInfoCard(nome = device.nome, online = device.online)
        }

        ThemeToggleCard(darkTheme = darkTheme, onToggle = onToggleTheme)
    }
}

/**
 * Nome do dispositivo + estado online/offline — únicos dados de `DispositivoResponse` que o
 * protótipo (`base.html`) também mostra e que têm campo real no backend. Wi-Fi, nuvem e
 * firmware/OTA do protótipo ficam de fora: sem endpoint/campo correspondente hoje (mesmo critério
 * de "omitido em vez de inventado" já usado em `CareScreen`/`ScheduleScreen`).
 */
@Composable
private fun DeviceInfoCard(nome: String, online: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .background(CafeyTheme.colors.surface, CafeyTheme.shapes.large)
            .border(1.dp, CafeyTheme.colors.line, CafeyTheme.shapes.large)
            .padding(18.dp),
    ) {
        CafeyStar(color = CafeyTheme.colors.brand, size = 28.dp)
        Text(
            nome,
            style = CafeyTheme.typography.cardTitle,
            color = CafeyTheme.colors.ink,
            modifier = Modifier.padding(start = 12.dp).weight(1f),
        )
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

/** Issue #182 — alternador claro/escuro (`CafeyTheme.kt:31`), sem UI até então. */
@Composable
private fun ThemeToggleCard(darkTheme: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .background(CafeyTheme.colors.surface, CafeyTheme.shapes.large)
            .border(1.dp, CafeyTheme.colors.line, CafeyTheme.shapes.large)
            .padding(20.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Tema escuro", style = CafeyTheme.typography.cardTitle, color = CafeyTheme.colors.ink)
            Text(
                "Aplica em todo o app, nesta conta.",
                style = CafeyTheme.typography.bodySmall,
                color = CafeyTheme.colors.muted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Switch(
            checked = darkTheme,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(checkedTrackColor = CafeyTheme.colors.brand),
        )
    }
}
