package br.com.tavaressan.cafey.shared.ui.ble

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme

/**
 * Exibe o bloqueio de prontidão BLE (permissões/Bluetooth) com ações sob demanda.
 * Reaproveitado pelo onboarding (etapa 3) e por telas com ação BLE (cadastro/provisionamento).
 * Quando [status] fica pronto, chama [onReady] uma vez por transição.
 */
@Composable
fun BleReadinessGate(
    status: BlePermissionStatus,
    controller: BlePermissionController,
    explanation: String,
    onReady: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(status.ready) {
        if (status.ready) onReady()
    }
    Column(modifier = modifier) {
        when (status.readinessAction()) {
            BleReadinessAction.Unsupported -> {
                BlePromptCard(
                    title = "Bluetooth indisponível",
                    body = explanation + "\n\nEste dispositivo/plataforma não oferece Bluetooth LE para pareamento.",
                    primaryLabel = null,
                    onPrimary = null,
                    secondaryLabel = "Configurações de Bluetooth",
                    onSecondary = controller::openBluetoothSettings,
                )
            }
            BleReadinessAction.RequestPermissions -> {
                BlePromptCard(
                    title = "Permissão necessária",
                    body = explanation,
                    primaryLabel = "Permitir Bluetooth",
                    onPrimary = controller::requestPermissions,
                )
            }
            BleReadinessAction.OpenAppSettings -> {
                BlePromptCard(
                    title = "Permissão necessária",
                    body = "A permissão foi negada e o sistema não pode perguntar novamente. Abra as configurações do app para conceder a permissão.",
                    primaryLabel = "Abrir configurações do app",
                    onPrimary = controller::openAppSettings,
                    secondaryLabel = "Configurações de Bluetooth",
                    onSecondary = controller::openBluetoothSettings,
                )
            }
            BleReadinessAction.EnableBluetooth -> {
                BlePromptCard(
                    title = "Bluetooth desligado",
                    body = "Ligue o Bluetooth para localizar a base. O app pedirá a confirmação do sistema — o Bluetooth nunca é ligado silenciosamente.",
                    primaryLabel = "Ligar Bluetooth",
                    onPrimary = controller::requestBluetoothEnable,
                    secondaryLabel = "Configurações de Bluetooth",
                    onSecondary = controller::openBluetoothSettings,
                )
            }
            BleReadinessAction.Ready -> Unit
        }
    }
}

@Composable
fun BlePromptCard(
    title: String,
    body: String,
    primaryLabel: String?,
    onPrimary: (() -> Unit)?,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = CafeyTheme.colors.surface),
        border = BorderStroke(1.5.dp, CafeyTheme.colors.brand),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = CafeyTheme.typography.cardTitle, color = CafeyTheme.colors.ink)
            Text(body, style = CafeyTheme.typography.body, color = CafeyTheme.colors.muted, modifier = Modifier.padding(top = 6.dp))
            if (primaryLabel != null && onPrimary != null) {
                Button(
                    onClick = onPrimary,
                    colors = ButtonDefaults.buttonColors(containerColor = CafeyTheme.colors.brand),
                    shape = CafeyTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) { Text(primaryLabel) }
            }
            if (secondaryLabel != null && onSecondary != null) {
                OutlinedButton(
                    onClick = onSecondary,
                    shape = CafeyTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text(secondaryLabel) }
            }
        }
    }
}
