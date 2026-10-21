package br.com.tavaressan.cafey.shared.ui.device

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.tavaressan.cafey.shared.LocalAppContainer
import br.com.tavaressan.cafey.shared.domain.validation.FieldError
import br.com.tavaressan.cafey.shared.ui.auth.fieldErrorMessage
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme

/**
 * Formulário de dispositivo: cadastro assistido por BLE (UC-04 #249), cadastro manual (APP-13)
 * e edição/exclusão quando [deviceId] é preenchido.
 */
@Composable
fun DeviceFormScreen(deviceId: String? = null, onDone: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = viewModel<DeviceFormViewModel>(
        factory = viewModelFactory {
            initializer {
                DeviceFormViewModel(
                    deviceApi = container.deviceApi,
                    bleClient = container.bleClient,
                    deviceId = deviceId,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.success) {
        if (state.success) onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CafeyTheme.colors.ground)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        when (state.step) {
            DeviceFormStep.BLE_SEARCH -> BleSearchStep(
                state = state,
                onSelectDevice = viewModel::selectDevice,
                onRefreshScan = viewModel::startBleScan,
                onSwitchToManual = viewModel::switchToManual,
            )
            DeviceFormStep.WIFI_CREDENTIALS -> WifiCredentialsStep(
                state = state,
                onSsidChange = viewModel::onWifiSsidChange,
                onPasswordChange = viewModel::onWifiPasswordChange,
                onTogglePasswordVisibility = viewModel::toggleWifiPasswordVisibility,
                onConnect = viewModel::provisionWifi,
                onBack = { viewModel.setStep(DeviceFormStep.BLE_SEARCH) },
            )
            DeviceFormStep.WIFI_CONNECTING -> WifiConnectingStep(state = state)
            DeviceFormStep.CONFIRM_REGISTER -> ConfirmRegisterStep(
                state = state,
                onNomeChange = viewModel::onNomeChange,
                onSave = viewModel::save,
            )
            DeviceFormStep.MANUAL -> ManualFormStep(
                state = state,
                viewModel = viewModel,
                onConfirmDelete = { confirmDelete = true },
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir dispositivo?") },
            text = { Text("Os agendamentos e o histórico de preparos deste dispositivo também serão apagados.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) {
                    Text("Excluir", color = CafeyTheme.colors.brandDeep)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun BleSearchStep(
    state: DeviceFormUiState,
    onSelectDevice: (br.com.tavaressan.cafey.shared.ble.BleDispositivo) -> Unit,
    onRefreshScan: () -> Unit,
    onSwitchToManual: () -> Unit,
) {
    Text("Buscar Cafeteira via Bluetooth", style = CafeyTheme.typography.screenTitle, color = CafeyTheme.colors.ink)
    Text(
        "Certifique-se de que a cafeteira está ligada e com o Bluetooth ativo nas proximidades.",
        style = CafeyTheme.typography.bodySmall,
        color = CafeyTheme.colors.muted,
        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
    )

    if (state.scanning) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 16.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = CafeyTheme.colors.brand)
            Text("Procurando cafeteiras Cafey...", style = CafeyTheme.typography.bodySmall, color = CafeyTheme.colors.muted)
        }
    }

    state.errorMessage?.let {
        Text(it, color = CafeyTheme.colors.brandDeep, modifier = Modifier.padding(bottom = 12.dp))
    }

    if (state.discoveredDevices.isEmpty() && !state.scanning) {
        Text(
            "Nenhuma cafeteira Cafey encontrada.",
            style = CafeyTheme.typography.body,
            color = CafeyTheme.colors.muted,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        Button(
            onClick = onRefreshScan,
            colors = ButtonDefaults.buttonColors(containerColor = CafeyTheme.colors.brand),
            shape = CafeyTheme.shapes.small,
        ) {
            Text("Buscar novamente")
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.discoveredDevices.forEach { device ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectDevice(device) },
                    colors = CardDefaults.cardColors(containerColor = CafeyTheme.colors.ground),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(device.nome, style = CafeyTheme.typography.cardTitle, color = CafeyTheme.colors.ink)
                            Text("Sinal: ${device.rssi} dBm", style = CafeyTheme.typography.bodySmall, color = CafeyTheme.colors.muted)
                        }
                        Button(
                            onClick = { onSelectDevice(device) },
                            colors = ButtonDefaults.buttonColors(containerColor = CafeyTheme.colors.brand),
                            shape = CafeyTheme.shapes.small,
                        ) {
                            Text("Conectar")
                        }
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))
    OutlinedButton(
        onClick = onSwitchToManual,
        modifier = Modifier.fillMaxWidth(),
        shape = CafeyTheme.shapes.small,
    ) {
        Text("Configurar manualmente com serial")
    }
}

@Composable
private fun WifiCredentialsStep(
    state: DeviceFormUiState,
    onSsidChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onConnect: () -> Unit,
    onBack: () -> Unit,
) {
    Text("Configurar Wi-Fi da Cafeteira", style = CafeyTheme.typography.screenTitle, color = CafeyTheme.colors.ink)
    Text(
        "Informe a rede Wi-Fi residencial de 2.4 GHz para conectar sua cafeteira.",
        style = CafeyTheme.typography.bodySmall,
        color = CafeyTheme.colors.muted,
        modifier = Modifier.padding(top = 8.dp),
    )

    state.selectedDevice?.let { dev ->
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            colors = CardDefaults.cardColors(containerColor = CafeyTheme.colors.brandTint),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Dispositivo: ${dev.nome}", style = CafeyTheme.typography.cardTitle, color = CafeyTheme.colors.ink)
                state.deviceInfo?.let { info ->
                    Text("Serial: ${info.serial} | Firmware: ${info.firmware}", style = CafeyTheme.typography.bodySmall, color = CafeyTheme.colors.muted)
                }
            }
        }
    }

    OutlinedTextField(
        value = state.wifiSsid,
        onValueChange = onSsidChange,
        label = { Text("Nome da rede Wi-Fi (SSID 2.4 GHz)") },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CafeyTheme.colors.brand),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    )

    OutlinedTextField(
        value = state.wifiPassword,
        onValueChange = onPasswordChange,
        label = { Text("Senha da rede Wi-Fi") },
        singleLine = true,
        visualTransformation = if (state.wifiPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            Text(
                if (state.wifiPasswordVisible) "Ocultar" else "Mostrar",
                style = CafeyTheme.typography.bodySmall,
                color = CafeyTheme.colors.brand,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .clickable { onTogglePasswordVisibility() },
            )
        },
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CafeyTheme.colors.brand),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    )

    state.errorMessage?.let {
        Text(it, color = CafeyTheme.colors.brandDeep, modifier = Modifier.padding(top = 12.dp))
    }

    Row(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onConnect,
            enabled = !state.wifiConnecting,
            colors = ButtonDefaults.buttonColors(containerColor = CafeyTheme.colors.brand),
            shape = CafeyTheme.shapes.small,
            modifier = Modifier.weight(1f),
        ) {
            Text("Conectar Wi-Fi")
        }
        OutlinedButton(
            onClick = onBack,
            enabled = !state.wifiConnecting,
            shape = CafeyTheme.shapes.small,
            modifier = Modifier.weight(1f),
        ) {
            Text("Voltar")
        }
    }
}

@Composable
private fun WifiConnectingStep(state: DeviceFormUiState) {
    Text("Conectando ao Wi-Fi...", style = CafeyTheme.typography.screenTitle, color = CafeyTheme.colors.ink)
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(color = CafeyTheme.colors.brand)
            Text(
                state.wifiStatusMessage ?: "Enviando credenciais e conectando...",
                style = CafeyTheme.typography.body,
                color = CafeyTheme.colors.ink,
            )
        }
    }
}

@Composable
private fun ConfirmRegisterStep(
    state: DeviceFormUiState,
    onNomeChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Text("Cafeteira Conectada!", style = CafeyTheme.typography.screenTitle, color = CafeyTheme.colors.ink)
    Text(
        "A cafeteira foi conectada com sucesso ao Wi-Fi! Defina um nome para identificá-la no aplicativo.",
        style = CafeyTheme.typography.bodySmall,
        color = CafeyTheme.colors.muted,
        modifier = Modifier.padding(top = 8.dp),
    )

    state.deviceInfo?.let { info ->
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            colors = CardDefaults.cardColors(containerColor = CafeyTheme.colors.brandTint),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Número Serial da Cafeteira:", style = CafeyTheme.typography.bodySmall, color = CafeyTheme.colors.muted)
                Text(info.serial, style = CafeyTheme.typography.cardTitle, color = CafeyTheme.colors.ink)
            }
        }
    }

    DeviceTextField(
        value = state.nome,
        onValueChange = onNomeChange,
        label = "Nome da cafeteira (ex.: Cozinha)",
        error = state.errors.nome,
    )

    state.errorMessage?.let {
        Text(it, color = CafeyTheme.colors.brandDeep, modifier = Modifier.padding(top = 12.dp))
    }

    Button(
        onClick = onSave,
        enabled = !state.saving,
        colors = ButtonDefaults.buttonColors(containerColor = CafeyTheme.colors.brand),
        shape = CafeyTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
    ) {
        if (state.saving) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = CafeyTheme.colors.brandOn)
        } else {
            Text("Concluir Cadastro")
        }
    }
}

@Composable
private fun ManualFormStep(
    state: DeviceFormUiState,
    viewModel: DeviceFormViewModel,
    onConfirmDelete: () -> Unit,
) {
    val isCreate = state.deviceId == null
    Text(
        if (isCreate) "Cadastrar dispositivo" else "Editar dispositivo",
        style = CafeyTheme.typography.screenTitle,
        color = CafeyTheme.colors.ink,
    )

    if (isCreate) {
        if (state.bleSupported) {
            Button(
                onClick = viewModel::switchToBle,
                colors = ButtonDefaults.buttonColors(containerColor = CafeyTheme.colors.brandTint),
                shape = CafeyTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text("Buscar cafeteira via Bluetooth", color = CafeyTheme.colors.brand)
            }
        } else {
            Text(
                "Dê um nome para sua cafeteira.",
                style = CafeyTheme.typography.bodySmall,
                color = CafeyTheme.colors.muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    if (state.loading) {
        Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = CafeyTheme.colors.brand)
        }
        return
    }

    DeviceTextField(state.nome, viewModel::onNomeChange, "Nome", state.errors.nome)
    DeviceTextField(state.timezone, viewModel::onTimezoneChange, "Fuso horário (ex.: America/Sao_Paulo)", error = null)
    DeviceTextField(
        state.duracaoPreparoS,
        viewModel::onDuracaoPreparoSChange,
        "Duração do preparo (segundos)",
        state.errors.duracaoPreparoS,
        numeric = true,
    )
    DeviceTextField(
        state.limiarDescalcificacao,
        viewModel::onLimiarDescalcificacaoChange,
        "Descalcificar a cada (preparos)",
        state.errors.limiarDescalcificacao,
        numeric = true,
    )

    state.errorMessage?.let {
        Text(it, color = CafeyTheme.colors.brandDeep, modifier = Modifier.padding(top = 12.dp))
    }

    Row(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = viewModel::save,
            enabled = !state.saving,
            colors = ButtonDefaults.buttonColors(containerColor = CafeyTheme.colors.brand),
            shape = CafeyTheme.shapes.small,
            modifier = Modifier.weight(1f),
        ) {
            if (state.saving) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = CafeyTheme.colors.brandOn)
            } else {
                Text("Gravar")
            }
        }
        OutlinedButton(
            onClick = viewModel::clearFields,
            enabled = !state.saving,
            shape = CafeyTheme.shapes.small,
            modifier = Modifier.weight(1f),
        ) {
            Text("Limpar Campos")
        }
    }

    if (!isCreate) {
        TextButton(
            onClick = onConfirmDelete,
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text("Excluir dispositivo", color = CafeyTheme.colors.brandDeep)
        }
    }
}

@Composable
private fun DeviceTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: FieldError?,
    numeric: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = { fieldErrorMessage(error)?.let { Text(it) } },
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CafeyTheme.colors.brand),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    )
}
