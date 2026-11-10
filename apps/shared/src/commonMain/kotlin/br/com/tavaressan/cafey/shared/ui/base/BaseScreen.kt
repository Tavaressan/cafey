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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import br.com.tavaressan.cafey.shared.domain.model.DiagnosticoDispositivoResponse
import br.com.tavaressan.cafey.shared.domain.model.DispositivoResponse
import br.com.tavaressan.cafey.shared.domain.model.PapelDispositivo
import br.com.tavaressan.cafey.shared.ui.theme.CafeyLogo
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme

@Composable
fun BaseScreen(
    onGoToAccount: () -> Unit = {},
    onEditDevice: (String) -> Unit = {},
    onRegisterDevice: () -> Unit = {},
    onViewTutorial: () -> Unit = {},
) {
    val container = LocalAppContainer.current
    val viewModel = viewModel<BaseViewModel>(
        factory = viewModelFactory { initializer { BaseViewModel(container.deviceApi, container.themePreference, container.activeDeviceId) } },
    )
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadDevice() }

    BaseContent(
        loading = state.loading,
        device = state.device,
        diagnostico = state.diagnostico,
        diagnosticoRestrito = state.diagnosticoRestrito,
        darkTheme = state.darkTheme,
        errorMessage = state.errorMessage,
        onToggleTheme = viewModel::toggleTheme,
        onGoToAccount = onGoToAccount,
        onEditDevice = { state.device?.let { onEditDevice(it.id) } },
        onRegisterDevice = onRegisterDevice,
        onViewTutorial = onViewTutorial,
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
    onGoToAccount: () -> Unit = {},
    onEditDevice: () -> Unit = {},
    onRegisterDevice: () -> Unit = {},
    diagnostico: DiagnosticoDispositivoResponse? = null,
    diagnosticoRestrito: Boolean = false,
    onViewTutorial: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CafeyTheme.colors.ground)
            .verticalScroll(rememberScrollState())
            .padding(22.dp)
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
            if (device.papel == PapelDispositivo.PROPRIETARIO) {
                DiagnosticoCard(diagnostico = diagnostico, restrito = diagnosticoRestrito)
            }
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

        // Issue #183 — a tela de Conta (logout) não é uma aba do rodapé/trilho/sidebar (nav.js só
        // lista 5, todas espelhadas na issue #182); "Base" é o ponto de entrada natural.
        OutlinedButton(onClick = onGoToAccount, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Text("Conta")
        }
        OutlinedButton(onClick = onViewTutorial, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Text("Ver tutorial")
        }
    }
}

/** Issue #321 — painel de diagnóstico do módulo, com valores reais ou "indisponível" explícito. */
@Composable
private fun DiagnosticoCard(diagnostico: DiagnosticoDispositivoResponse?, restrito: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .background(CafeyTheme.colors.surface, CafeyTheme.shapes.large)
            .border(1.dp, CafeyTheme.colors.line, CafeyTheme.shapes.large)
            .padding(18.dp),
    ) {
        Text("Diagnóstico do módulo", style = CafeyTheme.typography.cardTitle, color = CafeyTheme.colors.ink)
        if (diagnostico == null) {
            val msg = if (restrito) "Indisponível no momento." else "Carregando diagnóstico..."
            Text(
                msg,
                style = CafeyTheme.typography.bodySmall,
                color = CafeyTheme.colors.muted,
                modifier = Modifier.padding(top = 8.dp),
            )
            return@Column
        }

        if (!diagnostico.online) {
            Text(
                "Base offline — últimos valores conhecidos em ${diagnostico.ultimoVisto ?: "indisponível"}.",
                style = CafeyTheme.typography.bodySmall,
                color = CafeyTheme.colors.brandDeep,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        DiagnosticoItem("Conexão Wi-Fi (RSSI)", diagnostico.rssi?.let { "$it dBm" } ?: "Indisponível")
        DiagnosticoItem("Conexão com a nuvem", if (diagnostico.online) "Online" else "Offline")
        DiagnosticoItem("Última comunicação", diagnostico.ultimoVisto ?: "Indisponível")
        DiagnosticoItem("Estado do módulo", diagnostico.estado.ifBlank { "Indisponível" })
        DiagnosticoItem("Estado do relé", diagnostico.estadoRele ?: "Indisponível")
        DiagnosticoItem("Versão do firmware", diagnostico.firmware ?: "Indisponível")
        DiagnosticoItem("Tempo ligado (uptime)", diagnostico.uptimeS?.let { "${it}s" } ?: "Indisponível")
        DiagnosticoItem("Versão dos agendamentos", diagnostico.versaoAgendamentos.toString())

        Text(
            "Erros recentes",
            style = CafeyTheme.typography.bodySmall,
            color = CafeyTheme.colors.ink,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (diagnostico.errosRecentes.isEmpty()) {
            Text("Nenhum erro recente.", style = CafeyTheme.typography.bodySmall, color = CafeyTheme.colors.muted, modifier = Modifier.padding(top = 4.dp))
        } else {
            diagnostico.errosRecentes.forEach { erro ->
                val detalhe = erro.detalheErro?.let { " — $it" } ?: ""
                Text(
                    "${erro.timestamp} (${erro.origem})$detalhe",
                    style = CafeyTheme.typography.bodySmall,
                    color = CafeyTheme.colors.muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun DiagnosticoItem(rotulo: String, valor: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Top) {
        Text(
            rotulo,
            style = CafeyTheme.typography.bodySmall,
            color = CafeyTheme.colors.muted,
            modifier = Modifier.weight(1f),
        )
        Text(
            valor,
            style = CafeyTheme.typography.bodySmall,
            color = CafeyTheme.colors.ink,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/**
 * Nome do dispositivo + estado online/offline.
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
