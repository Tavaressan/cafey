package br.com.tavaressan.cafey.shared.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.tavaressan.cafey.shared.LocalAppContainer
import br.com.tavaressan.cafey.shared.ui.auth.fieldErrorMessage
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme

/**
 * APP-13 — cadastro manual de dispositivo. Recorte mínimo: só o nome da base é pedido; o
 * provisionamento via Bluetooth/Wi-Fi fica para quando o cliente BLE (APP-11) e o firmware
 * suportarem — não é inventado aqui.
 */
@Composable
fun DeviceRegisterScreen(onRegistered: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = viewModel<DeviceRegisterViewModel>(
        factory = viewModelFactory { initializer { DeviceRegisterViewModel(container.deviceApi) } },
    )
    val state by viewModel.uiState.collectAsState()

    // Navegar é efeito colateral: chamar no corpo do composable dispararia a cada recomposição.
    LaunchedEffect(state.success) {
        if (state.success) onRegistered()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CafeyTheme.colors.ground)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Cadastrar dispositivo", style = CafeyTheme.typography.screenTitle, color = CafeyTheme.colors.ink)

        Text(
            "Dê um nome para sua cafeteira. O pareamento automático via Bluetooth chega em uma atualização futura.",
            style = CafeyTheme.typography.bodySmall,
            color = CafeyTheme.colors.muted,
            modifier = Modifier.padding(top = 8.dp),
        )

        OutlinedTextField(
            value = state.nome,
            onValueChange = viewModel::onNomeChange,
            label = { Text("Nome") },
            isError = state.errors.nome != null,
            supportingText = { fieldErrorMessage(state.errors.nome)?.let { Text(it) } },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CafeyTheme.colors.brand),
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        )

        state.errorMessage?.let {
            Text(it, color = CafeyTheme.colors.brandDeep, modifier = Modifier.padding(top = 12.dp))
        }

        Button(
            onClick = viewModel::submit,
            enabled = !state.saving,
            colors = ButtonDefaults.buttonColors(containerColor = CafeyTheme.colors.brand),
            shape = CafeyTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        ) {
            if (state.saving) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = CafeyTheme.colors.brandOn)
            } else {
                Text("Cadastrar")
            }
        }
    }
}
