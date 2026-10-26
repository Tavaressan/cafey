package br.com.tavaressan.cafey.shared.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.tavaressan.cafey.shared.LocalAppContainer
import br.com.tavaressan.cafey.shared.domain.model.CompartilhamentoResponse
import br.com.tavaressan.cafey.shared.ui.auth.fieldErrorMessage
import br.com.tavaressan.cafey.shared.ui.theme.CafeyLightColors
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme

/**
 * Seção de gerenciamento e compartilhamento de dispositivo com convidados (UC-05).
 * Exibida nas configurações/edição de um dispositivo existente.
 */
@Composable
fun DeviceGuestsSection(deviceId: String) {
    val container = LocalAppContainer.current
    val viewModel = viewModel<DeviceGuestsViewModel>(
        key = "DeviceGuests_$deviceId",
        factory = viewModelFactory {
            initializer {
                DeviceGuestsViewModel(
                    deviceId = deviceId,
                    deviceApi = container.deviceApi,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()
    var guestToRemove by remember { mutableStateOf<CompartilhamentoResponse?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
            .background(CafeyTheme.colors.surface, CafeyTheme.shapes.large)
            .border(1.dp, CafeyTheme.colors.line, CafeyTheme.shapes.large)
            .padding(18.dp),
    ) {
        Text(
            text = "Compartilhamento com convidados",
            style = CafeyTheme.typography.cardTitle,
            color = CafeyTheme.colors.ink,
        )
        Text(
            text = "Convide pessoas por e-mail para acessarem e comandarem esta cafeteira.",
            style = CafeyTheme.typography.bodySmall,
            color = CafeyTheme.colors.muted,
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
        )

        // Formulário de convite
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.emailInput,
                onValueChange = viewModel::onEmailChange,
                label = { Text("E-mail do convidado") },
                singleLine = true,
                isError = state.emailError != null,
                supportingText = {
                    fieldErrorMessage(state.emailError)?.let { Text(it) }
                },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CafeyTheme.colors.brand),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = viewModel::inviteGuest,
                enabled = !state.isInviting && state.emailInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CafeyTheme.colors.brand),
                shape = CafeyTheme.shapes.small,
            ) {
                if (state.isInviting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = CafeyTheme.colors.brandOn)
                } else {
                    Text("Convidar")
                }
            }
        }

        // Mensagens de erro e sucesso
        state.errorMessage?.let { errorMsg ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .background(CafeyTheme.colors.brandTint, CafeyTheme.shapes.small)
                    .padding(10.dp),
            ) {
                Text(errorMsg, color = CafeyLightColors.brandDeep, style = CafeyTheme.typography.bodySmall)
            }
        }

        state.successMessage?.let { successMsg ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .background(CafeyTheme.colors.surface, CafeyTheme.shapes.small)
                    .border(1.dp, CafeyTheme.colors.brand, CafeyTheme.shapes.small)
                    .padding(10.dp),
            ) {
                Text(successMsg, color = CafeyTheme.colors.brand, style = CafeyTheme.typography.bodySmall)
            }
        }

        // Lista de convidados
        if (state.loading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = CafeyTheme.colors.brand)
            }
        } else if (state.guests.isEmpty()) {
            Text(
                text = "Nenhum convidado vinculado a esta cafeteira.",
                style = CafeyTheme.typography.bodySmall,
                color = CafeyTheme.colors.muted,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.guests.forEach { guest ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CafeyTheme.colors.ground, CafeyTheme.shapes.small)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = guest.nome.ifBlank { guest.email },
                                style = CafeyTheme.typography.body,
                                color = CafeyTheme.colors.ink,
                            )
                            Text(
                                text = guest.email,
                                style = CafeyTheme.typography.bodySmall,
                                color = CafeyTheme.colors.muted,
                            )
                        }
                        OutlinedButton(
                            onClick = { guestToRemove = guest },
                            enabled = !state.isRemoving,
                            shape = CafeyTheme.shapes.small,
                        ) {
                            Text("Remover", color = CafeyTheme.colors.brandDeep, style = CafeyTheme.typography.caption)
                        }
                    }
                }
            }
        }
    }

    // Confirmação para remoção de vínculo de convidado
    guestToRemove?.let { guest ->
        AlertDialog(
            onDismissRequest = { guestToRemove = null },
            title = { Text("Remover convidado?") },
            text = {
                Text("Deseja remover o acesso de \"${guest.nome.ifBlank { guest.email }}\" (${guest.email}) a este dispositivo?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = guest.usuarioId
                        guestToRemove = null
                        viewModel.removeGuest(id)
                    },
                ) {
                    Text("Remover", color = CafeyTheme.colors.brandDeep)
                }
            },
            dismissButton = {
                TextButton(onClick = { guestToRemove = null }) {
                    Text("Cancelar")
                }
            },
        )
    }
}
