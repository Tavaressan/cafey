package br.com.tavaressan.cafey.shared.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.tavaressan.cafey.shared.LocalAppContainer
import br.com.tavaressan.cafey.shared.ui.ble.BlePermissionController
import br.com.tavaressan.cafey.shared.ui.ble.BlePermissionStatus
import br.com.tavaressan.cafey.shared.ui.ble.BlePromptCard
import br.com.tavaressan.cafey.shared.ui.ble.BleReadinessAction
import br.com.tavaressan.cafey.shared.ui.ble.readinessAction
import br.com.tavaressan.cafey.shared.ui.ble.rememberBlePermissionController
import br.com.tavaressan.cafey.shared.ui.theme.CafeyLogo
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme

/**
 * Onboarding interativo de primeira utilização (issue #312, app Android).
 * [onFinished] é chamado ao concluir/pular/fechar (após persistir). [onProvisionWifi] navega
 * para o fluxo de pareamento/provisionamento BLE reaproveitando a tela de cadastro de dispositivo.
 */
@Composable
fun OnboardingScreen(
    revisit: Boolean = false,
    onFinished: () -> Unit,
    onProvisionWifi: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel = viewModel<OnboardingViewModel>(
        factory = viewModelFactory {
            initializer {
                OnboardingViewModel(
                    markCompleted = { container.onboardingPreference.markCompleted() },
                    revisit = revisit,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()
    val bleController = rememberBlePermissionController(onStatusChange = viewModel::updateBleStatus)
    var pendingProvision by remember { mutableStateOf(false) }

    LaunchedEffect(state.finished, pendingProvision) {
        if (state.finished) {
            if (pendingProvision) onProvisionWifi() else onFinished()
        }
    }

    OnboardingContent(
        state = state,
        bleController = bleController,
        onBack = viewModel::back,
        onNext = viewModel::next,
        onSkip = viewModel::skip,
        onComplete = viewModel::complete,
        onDismiss = viewModel::dismiss,
        onProvisionWifi = {
            pendingProvision = true
            viewModel.complete()
        },
    )
}

@Composable
internal fun OnboardingContent(
    state: OnboardingUiState,
    bleController: BlePermissionController,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onComplete: () -> Unit,
    onDismiss: () -> Unit,
    onProvisionWifi: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CafeyTheme.colors.ground)
            .padding(24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Passo ${state.step.index + 1} de ${OnboardingStep.TOTAL}",
                style = CafeyTheme.typography.caption,
                color = CafeyTheme.colors.muted,
            )
            TextButton(onClick = if (state.revisit) onDismiss else onSkip) {
                Text(if (state.revisit) "Fechar" else "Pular", color = CafeyTheme.colors.muted)
            }
        }
        LinearProgressIndicator(
            progress = { (state.step.index + 1).toFloat() / OnboardingStep.TOTAL },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            color = CafeyTheme.colors.brand,
            trackColor = CafeyTheme.colors.line,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 20.dp),
        ) {
            when (state.step) {
                OnboardingStep.CONHECENDO -> StepConhecendo()
                OnboardingStep.CONFIGURANDO_BASE -> StepConfigurandoBase()
                OnboardingStep.BLUETOOTH_WIFI -> StepBluetoothWifi(
                    status = state.bleStatus,
                    controller = bleController,
                    onProvisionWifi = onProvisionWifi,
                )
                OnboardingStep.PRIMEIRO_CAFE -> StepPrimeiroCafe(onProvisionWifi = onProvisionWifi)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.canGoBack) {
                OutlinedButton(
                    onClick = onBack,
                    shape = CafeyTheme.shapes.small,
                    modifier = Modifier.weight(1f),
                ) { Text("Voltar") }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            if (state.isLastStep) {
                Button(
                    onClick = onComplete,
                    colors = ButtonDefaults.buttonColors(containerColor = CafeyTheme.colors.brand),
                    shape = CafeyTheme.shapes.small,
                    modifier = Modifier.weight(1f),
                ) { Text("Concluir") }
            } else {
                Button(
                    onClick = onNext,
                    colors = ButtonDefaults.buttonColors(containerColor = CafeyTheme.colors.brand),
                    shape = CafeyTheme.shapes.small,
                    modifier = Modifier.weight(1f),
                ) { Text("Avançar") }
            }
        }
    }
}

@Composable
private fun StepConhecendo() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        CafeyLogo(size = 56.dp)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Conhecendo o Cafey", style = CafeyTheme.typography.screenTitle, color = CafeyTheme.colors.ink)
    }
    Spacer(modifier = Modifier.height(16.dp))
    InfoCard(
        title = "Sua cafeteira, no seu ritmo",
        body = "O Cafey conecta a base da sua cafeteira ao aplicativo para acompanhar preparos, agendar horários e cuidar da manutenção.",
    )
    InfoCard(
        title = "Como funciona",
        body = "A base física se comunica com o app. Para configurar o Wi-Fi da base pela primeira vez, o app usa Bluetooth — com a sua permissão, explicada no passo 3.",
    )
}

@Composable
private fun StepConfigurandoBase() {
    Text("Configurando a base física", style = CafeyTheme.typography.screenTitle, color = CafeyTheme.colors.ink)
    Spacer(modifier = Modifier.height(12.dp))
    InfoCard(
        title = "Alimentação",
        body = "Conecte a base à energia. Com a alimentação ligada, o LED RGB da base indica o estado do sistema.",
    )
    InfoCard(
        title = "Botão físico (RGB)",
        body = "A base possui um botão físico. Use-o para interações locais conforme o hardware desta versão. Mantenha a base ligada e próxima ao celular durante a configuração inicial.",
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text("Estados do LED da base (firmware)", style = CafeyTheme.typography.cardTitle, color = CafeyTheme.colors.ink)
    Text(
        "A cor indica o estado da cafeteira; o piscar indica problema de rede.",
        style = CafeyTheme.typography.bodySmall,
        color = CafeyTheme.colors.muted,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
    )
    LedStateRow("Azul respirando (lento)", "Conectado e ocioso")
    LedStateRow("Amarelo piscando", "Sem Wi-Fi, ocioso (operando offline)")
    LedStateRow("Verde fixo", "Agendamento armado (conectado)")
    LedStateRow("Âmbar fixo", "Desconectado, com agendamento armado")
    LedStateRow("Verde pulsando", "Preparando café agora")
    LedStateRow("Branco fixo", "Café pronto (recém-concluído)")
    LedStateRow("Vermelho fixo", "Erro ou falha")
}

@Composable
private fun StepBluetoothWifi(
    status: BlePermissionStatus,
    controller: BlePermissionController,
    onProvisionWifi: () -> Unit,
) {
    Text("Bluetooth e Wi-Fi", style = CafeyTheme.typography.screenTitle, color = CafeyTheme.colors.ink)
    Spacer(modifier = Modifier.height(12.dp))
    InfoCard(
        title = "Por que pedir Bluetooth e permissões?",
        body = "O provisionamento do Wi-Fi da base é feito pelo aplicativo via Bluetooth (BLE). Para localizar a base próxima, o Android exige permissão de Bluetooth (e, em versões antigas, localização). As permissões só são solicitadas agora, após esta explicação.",
    )

    when (status.readinessAction()) {
        BleReadinessAction.Unsupported -> BlePromptCard(
            title = "Bluetooth indisponível",
            body = "Este dispositivo/plataforma não oferece Bluetooth LE para o provisionamento. Você pode continuar e configurar manualmente quando houver suporte.",
            primaryLabel = null,
            onPrimary = null,
            secondaryLabel = "Abrir configurações de Bluetooth",
            onSecondary = controller::openBluetoothSettings,
        )
        BleReadinessAction.RequestPermissions -> BlePromptCard(
            title = "Permissão necessária",
            body = "Conceda a permissão para que o app localize a base Cafey nas proximidades. Nenhum acesso é feito antes da sua confirmação.",
            primaryLabel = "Permitir Bluetooth",
            onPrimary = controller::requestPermissions,
        )
        BleReadinessAction.OpenAppSettings -> BlePromptCard(
            title = "Permissão necessária",
            body = "A permissão de Bluetooth foi negada e o sistema não pode perguntar novamente. Abra as configurações do app para conceder a permissão e concluir a etapa.",
            primaryLabel = "Abrir configurações do app",
            onPrimary = controller::openAppSettings,
            secondaryLabel = "Abrir configurações de Bluetooth",
            onSecondary = controller::openBluetoothSettings,
        )
        BleReadinessAction.EnableBluetooth -> BlePromptCard(
            title = "Bluetooth desligado",
            body = "Ligue o Bluetooth para localizar a base. O app pedirá a confirmação do sistema para ligar — o Bluetooth nunca é ligado silenciosamente. Como alternativa, você pode abrir as configurações.",
            primaryLabel = "Ligar Bluetooth",
            onPrimary = controller::requestBluetoothEnable,
            secondaryLabel = "Abrir configurações de Bluetooth",
            onSecondary = controller::openBluetoothSettings,
        )
        BleReadinessAction.Ready -> BlePromptCard(
            title = "Pronto para provisionar",
            body = "Permissões concedidas e Bluetooth ligado. Você pode provisionar o Wi-Fi da base agora (reaproveitando o pareamento BLE) ou avançar e fazer isso depois pela tela Base.",
            primaryLabel = "Provisionar Wi-Fi agora",
            onPrimary = onProvisionWifi,
            secondaryLabel = "Recalcular estado",
            onSecondary = controller::refresh,
        )
    }
}

@Composable
private fun StepPrimeiroCafe(onProvisionWifi: () -> Unit) {
    Text("Preparando o primeiro café", style = CafeyTheme.typography.screenTitle, color = CafeyTheme.colors.ink)
    Spacer(modifier = Modifier.height(12.dp))
    InfoCard(
        title = "Tudo pronto para começar",
        body = "Com a base configurada e conectada ao Wi-Fi, você pode preparar seu café pelo app, criar agendamentos e acompanhar o histórico.",
    )
    InfoCard(
        title = "Se ainda não provisionou",
        body = "Você pode provisionar o Wi-Fi/pairing BLE a qualquer momento pela tela Base (ou pelo cadastro de dispositivo).",
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(
        onClick = onProvisionWifi,
        shape = CafeyTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Abrir pareamento/provisionamento BLE") }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        colors = CardDefaults.cardColors(containerColor = CafeyTheme.colors.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CafeyTheme.colors.line),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = CafeyTheme.typography.cardTitle, color = CafeyTheme.colors.ink)
            Text(body, style = CafeyTheme.typography.body, color = CafeyTheme.colors.muted, modifier = Modifier.padding(top = 6.dp))
        }
    }
}



@Composable
private fun LedStateRow(state: String, meaning: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, CafeyTheme.colors.line, CafeyTheme.shapes.small)
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(CafeyTheme.colors.brand),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(state, style = CafeyTheme.typography.bodySmall, color = CafeyTheme.colors.ink, fontWeight = FontWeight.SemiBold)
            Text(meaning, style = CafeyTheme.typography.bodySmall, color = CafeyTheme.colors.muted)
        }
    }
}
