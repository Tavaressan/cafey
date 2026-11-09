package br.com.tavaressan.cafey.shared.ui.base

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import br.com.tavaressan.cafey.shared.ble.WifiNetworksInfo
import br.com.tavaressan.cafey.shared.domain.model.DispositivoResponse
import br.com.tavaressan.cafey.shared.domain.model.PapelDispositivo
import br.com.tavaressan.cafey.shared.ui.theme.CafeyLogo
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme

@Composable
fun BaseScreen(
    onGoToAccount: () -> Unit = {},
    onEditDevice: (String) -> Unit = {},
    onRegisterDevice: () -> Unit = {},
) {
    val container = LocalAppContainer.current
    val viewModel = viewModel<BaseViewModel>(
        factory = viewModelFactory {
            initializer {
                BaseViewModel(
                    deviceApi = container.deviceApi,
                    themePreference = container.themePreference,
                    activeDeviceId = container.activeDeviceId,
                    commandApi = container.commandApi,
                    bleClient = container.bleClient,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadDevice() }

    BaseContent(
        loading = state.loading,
        device = state.device,
        darkTheme = state.darkTheme,
        errorMessage = state.errorMessage,
        wifiRedes = state.wifiRedes,
        wifiCarregando = state.wifiCarregando,
        wifiOperacaoEmAndamento = state.wifiOperacaoEmAndamento,
        wifiErro = state.wifiErro,
        confirmarEsquecimento = state.confirmarEsquecimento,
        bleDisponivel = container.bleClient != null,
        onToggleTheme = viewModel::toggleTheme,
        onGoToAccount = onGoToAccount,
        onEditDevice = { state.device?.let { onEditDevice(it.id) } },
        onRegisterDevice = onRegisterDevice,
        onCarregarRedesWifi = viewModel::tentarCarregarRedesWifi,
        onSolicitarEsquecerRede = viewModel::solicitarEsquecerRede,
        onSolicitarEsquecerTodas = viewModel::solicitarEsquecerTodas,
        onCancelarConfirmacao = viewModel::cancelarConfirmacao,
        onConfirmarEsquecimento = { viewModel.confirmarEsquecimento() },
    )
}

/**
 * Corpo visual da tela "Base", separado de [BaseScreen] para ser testável por composição sem
 * `AppContainer`/rede.
 */
@Composable
internal fun BaseContent(
    loading: Boolean,
    device: DispositivoResponse?,
    darkTheme: Boolean,
    errorMessage: String?,
    wifiRedes: WifiNetworksInfo? = null,
    wifiCarregando: Boolean = false,
    wifiOperacaoEmAndamento: Boolean = false,
    wifiErro: String? = null,
    confirmarEsquecimento: String? = null,
    bleDisponivel: Boolean = false,
    onToggleTheme: () -> Unit,
    onGoToAccount: () -> Unit = {},
    onEditDevice: () -> Unit = {},
    onRegisterDevice: () -> Unit = {},
    onCarregarRedesWifi: () -> Unit = {},
    onSolicitarEsquecerRede: (String) -> Unit = {},
    onSolicitarEsquecerTodas: () -> Unit = {},
    onCancelarConfirmacao: () -> Unit = {},
    onConfirmarEsquecimento: () -> Unit = {},
) {
    if (confirmarEsquecimento != null) {
        val todas = confirmarEsquecimento == CONFIRMAR_ESQUECER_TODAS
        AlertDialog(
            onDismissRequest = onCancelarConfirmacao,
            title = { Text(if (todas) "Esquecer todas as redes?" else "Esquecer rede Wi-Fi?") },
            text = {
                Text(
                    if (todas) {
                        "A cafeteira vai esquecer todas as redes salvas e ficar offline, " +
                            "conectada só por Bluetooth. Para usar o Wi-Fi de novo, você precisa " +
                            "estar perto dela para provisionar outra rede."
                    } else {
                        "A cafeteira pode ficar offline ao sair da rede \"$confirmarEsquecimento\". " +
                            "Se for a rede atual e não houver outra salva, ela ficará só no Bluetooth. " +
                            "Para reconectar ao Wi-Fi, você precisa estar perto dela."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmarEsquecimento) { Text("Esquecer") }
            },
            dismissButton = {
                TextButton(onClick = onCancelarConfirmacao) { Text("Cancelar") }
            },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().background(CafeyTheme.colors.ground)
            .verticalScroll(rememberScrollState()).padding(22.dp)
    ) {
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
            Button(
                onClick = onRegisterDevice,
                colors = ButtonDefaults.buttonColors(containerColor = CafeyTheme.colors.brand),
                shape = CafeyTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                Text("Parear e Cadastrar Cafeteira (BLE)")
            }
        } else {
            DeviceInfoCard(nome = device.nome, online = device.online)
            WifiSection(
                device = device,
                wifiRedes = wifiRedes,
                wifiCarregando = wifiCarregando,
                wifiOperacaoEmAndamento = wifiOperacaoEmAndamento,
                wifiErro = wifiErro,
                bleDisponivel = bleDisponivel,
                onCarregarRedesWifi = onCarregarRedesWifi,
                onTrocarRede = onRegisterDevice,
                onSolicitarEsquecerRede = onSolicitarEsquecerRede,
                onSolicitarEsquecerTodas = onSolicitarEsquecerTodas,
            )
            Button(
                onClick = onRegisterDevice,
                colors = ButtonDefaults.buttonColors(containerColor = CafeyTheme.colors.brand),
                shape = CafeyTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            ) {
                Text("Provisionar Wi-Fi / Parear via BLE")
            }
            // O backend só deixa o proprietário alterar ou excluir o dispositivo.
            if (device.papel == PapelDispositivo.PROPRIETARIO) {
                OutlinedButton(onClick = onEditDevice, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Text("Editar dispositivo")
                }
            }
        }

        ThemeToggleCard(darkTheme = darkTheme, onToggle = onToggleTheme)

        OutlinedButton(onClick = onGoToAccount, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Text("Conta")
        }
    }
}

@Composable
private fun WifiSection(
    device: DispositivoResponse,
    wifiRedes: WifiNetworksInfo?,
    wifiCarregando: Boolean,
    wifiOperacaoEmAndamento: Boolean,
    wifiErro: String?,
    bleDisponivel: Boolean,
    onCarregarRedesWifi: () -> Unit,
    onTrocarRede: () -> Unit,
    onSolicitarEsquecerRede: (String) -> Unit,
    onSolicitarEsquecerTodas: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .background(CafeyTheme.colors.surface, CafeyTheme.shapes.large)
            .border(1.dp, CafeyTheme.colors.line, CafeyTheme.shapes.large)
            .padding(18.dp),
    ) {
        Text("Wi-Fi da cafeteira", style = CafeyTheme.typography.cardTitle, color = CafeyTheme.colors.ink)
        if (wifiRedes != null) {
            val atual = wifiRedes.ssid_atual
            Text(
                text = "Rede atual: ${atual ?: "nenhuma (aguardando provisionamento)"}",
                style = CafeyTheme.typography.bodySmall,
                color = CafeyTheme.colors.muted,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (wifiRedes.redes.isEmpty()) {
                Text(
                    "Nenhuma rede salva.",
                    style = CafeyTheme.typography.bodySmall,
                    color = CafeyTheme.colors.muted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            } else {
                Text(
                    "Redes salvas (sem senhas):",
                    style = CafeyTheme.typography.bodySmall,
                    color = CafeyTheme.colors.muted,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                wifiRedes.redes.forEach { ssid ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    ) {
                        val rotulo = if (ssid == atual) "$ssid (atual)" else ssid
                        Text(rotulo, style = CafeyTheme.typography.bodySmall, color = CafeyTheme.colors.ink,
                            modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = { onSolicitarEsquecerRede(ssid) },
                            enabled = !wifiOperacaoEmAndamento,
                        ) { Text("Esquecer") }
                    }
                }
            }
        } else {
            Text(
                "Veja a rede atual e as redes salvas conectando por Bluetooth perto da cafeteira. Senhas nunca saem da placa.",
                style = CafeyTheme.typography.bodySmall,
                color = CafeyTheme.colors.muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        wifiErro?.let {
            Text(it, color = CafeyTheme.colors.brandDeep, style = CafeyTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp))
        }

        if (wifiCarregando || wifiOperacaoEmAndamento) {
            CircularProgressIndicator(color = CafeyTheme.colors.brand, modifier = Modifier.padding(top = 10.dp).size(22.dp))
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onCarregarRedesWifi,
                enabled = bleDisponivel && !wifiCarregando && !wifiOperacaoEmAndamento,
                modifier = Modifier.weight(1f),
            ) { Text("Carregar redes (BLE)") }
            OutlinedButton(
                onClick = onTrocarRede,
                enabled = !wifiOperacaoEmAndamento,
                modifier = Modifier.weight(1f),
            ) { Text("Trocar rede") }
        }
        if (!bleDisponivel) {
            Text(
                "Bluetooth indisponível aqui; para ver redes salvas use um celular com Bluetooth.",
                style = CafeyTheme.typography.bodySmall,
                color = CafeyTheme.colors.muted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (wifiRedes != null && wifiRedes.redes.isNotEmpty() && device.papel == PapelDispositivo.PROPRIETARIO) {
            TextButton(
                onClick = onSolicitarEsquecerTodas,
                enabled = !wifiOperacaoEmAndamento,
                modifier = Modifier.padding(top = 2.dp),
            ) { Text("Esquecer todas as redes") }
        }
    }
}

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
        CafeyLogo(size = 32.dp)
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
