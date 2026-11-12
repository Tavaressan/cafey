package br.com.tavaressan.cafey.shared.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.tavaressan.cafey.shared.LocalAppContainer
import br.com.tavaressan.cafey.shared.ui.push.PushNotificationsSection
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme

/** Conta e segurança (issues #183, #261). */
@Composable
fun AccountScreen(onLoggedOut: () -> Unit, onViewTutorial: () -> Unit = {}) {
    val container = LocalAppContainer.current
    val viewModel = viewModel<AccountViewModel>(
        factory = viewModelFactory {
            initializer { AccountViewModel(container.authApi, container.pushRegistrationManager) }
        },
    )
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLoggedOut()
    }

    AccountContent(
        state = state,
        onLogout = viewModel::logout,
        onIniciarConfiguracao = viewModel::iniciarConfiguracao,
        onCodigoAtivacaoChange = viewModel::onCodigoAtivacaoChange,
        onConfirmarAtivacao = viewModel::confirmarAtivacao,
        onDispensarDialogo = viewModel::dispensarDialogoConfiguracao,
        onViewTutorial = onViewTutorial,
    )
}

@Composable
internal fun AccountContent(
    state: AccountUiState,
    onLogout: () -> Unit,
    onIniciarConfiguracao: () -> Unit,
    onCodigoAtivacaoChange: (String) -> Unit,
    onConfirmarAtivacao: () -> Unit,
    onDispensarDialogo: () -> Unit,
    onViewTutorial: () -> Unit = {},
) {
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CafeyTheme.colors.ground)
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
    ) {
        Text("Conta", style = CafeyTheme.typography.screenTitle, color = CafeyTheme.colors.ink)

        Text(
            "Segurança",
            style = CafeyTheme.typography.cardTitle,
            color = CafeyTheme.colors.ink,
            modifier = Modifier.padding(top = 24.dp),
        )
        val statusTexto = when (state.mfaHabilitado) {
            true -> "Autenticação em duas etapas: Ativo"
            false -> "Autenticação em duas etapas: Inativo"
            null -> if (state.mfaStatusLoading) "Autenticação em duas etapas: carregando..." else "Autenticação em duas etapas: —"
        }
        Text(
            statusTexto,
            style = CafeyTheme.typography.body,
            color = CafeyTheme.colors.ink,
            modifier = Modifier.padding(top = 8.dp),
        )

        state.mfaSuccessMessage?.let {
            Text(it, color = CafeyTheme.colors.brand, modifier = Modifier.padding(top = 8.dp))
        }
        state.mfaErrorMessage?.takeIf { !state.showConfigDialog }?.let {
            Text(it, color = CafeyTheme.colors.brandDeep, modifier = Modifier.padding(top = 8.dp))
        }

        Button(
            onClick = onIniciarConfiguracao,
            enabled = !state.mfaConfigLoading,
            colors = ButtonDefaults.buttonColors(containerColor = CafeyTheme.colors.brand),
            shape = CafeyTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            if (state.mfaConfigLoading) {
                CircularProgressIndicator(color = CafeyTheme.colors.brandOn)
            } else {
                Text(
                    if (state.mfaHabilitado == true) "Reconfigurar autenticação em duas etapas" else "Ativar autenticação em duas etapas",
                    color = CafeyTheme.colors.brandOn,
                )
            }
        }

        PushNotificationsSection()

        Button(
            onClick = onLogout,
            colors = ButtonDefaults.buttonColors(containerColor = CafeyTheme.colors.brand),
            shape = CafeyTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        ) {
            Text("Sair", color = CafeyTheme.colors.brandOn)
        }
        androidx.compose.material3.OutlinedButton(
            onClick = onViewTutorial,
            shape = CafeyTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            Text("Ver tutorial")
        }
    }

    if (state.showConfigDialog && state.configSegredo != null) {
        AlertDialog(
            onDismissRequest = onDispensarDialogo,
            title = { Text("Configurar autenticação em duas etapas") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Adicione esta chave no seu app autenticador (Google Authenticator, Authy etc.).")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            state.configSegredo,
                            modifier = Modifier.weight(1f),
                            style = CafeyTheme.typography.body,
                        )
                        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(state.configSegredo)) }) {
                            Text("Copiar")
                        }
                    }
                    OutlinedTextField(
                        value = state.codigoAtivacao,
                        onValueChange = onCodigoAtivacaoChange,
                        label = { Text("Código de confirmação (6 dígitos)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = state.mfaErrorMessage != null,
                        supportingText = { state.mfaErrorMessage?.let { Text(it) } },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CafeyTheme.colors.brand),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onConfirmarAtivacao,
                    enabled = !state.mfaActivateLoading && state.codigoAtivacao.length == 6,
                ) {
                    if (state.mfaActivateLoading) {
                        CircularProgressIndicator(color = CafeyTheme.colors.brandOn)
                    } else {
                        Text("Ativar")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDispensarDialogo) { Text("Cancelar") }
            },
        )
    }
}
