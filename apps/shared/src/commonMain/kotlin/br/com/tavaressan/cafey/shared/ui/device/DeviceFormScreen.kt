package br.com.tavaressan.cafey.shared.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.tavaressan.cafey.shared.LocalAppContainer
import br.com.tavaressan.cafey.shared.domain.validation.FieldError
import br.com.tavaressan.cafey.shared.ui.auth.fieldErrorMessage
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme

/**
 * Formulário de dispositivo: cadastro manual (APP-13) quando [deviceId] é `null`, edição e exclusão
 * quando preenchido. O provisionamento via Bluetooth/Wi-Fi fica para quando o cliente BLE (APP-11)
 * e o firmware suportarem — não é inventado aqui.
 */
@Composable
fun DeviceFormScreen(deviceId: String? = null, onDone: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = viewModel<DeviceFormViewModel>(
        factory = viewModelFactory { initializer { DeviceFormViewModel(container.deviceApi, deviceId) } },
    )
    val state by viewModel.uiState.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }

    // Navegar é efeito colateral: chamar no corpo do composable dispararia a cada recomposição.
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
        val isCreate = state.deviceId == null
        Text(
            if (isCreate) "Cadastrar dispositivo" else "Editar dispositivo",
            style = CafeyTheme.typography.screenTitle,
            color = CafeyTheme.colors.ink,
        )

        if (isCreate) {
            Text(
                "Dê um nome para sua cafeteira. O pareamento automático via Bluetooth chega em uma atualização futura.",
                style = CafeyTheme.typography.bodySmall,
                color = CafeyTheme.colors.muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (state.loading) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CafeyTheme.colors.brand)
            }
            return@Column
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
                onClick = { confirmDelete = true },
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text("Excluir dispositivo", color = CafeyTheme.colors.brandDeep)
            }
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
