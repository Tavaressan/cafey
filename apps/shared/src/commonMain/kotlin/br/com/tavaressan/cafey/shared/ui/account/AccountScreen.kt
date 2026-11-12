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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.tavaressan.cafey.shared.LocalAppContainer
import br.com.tavaressan.cafey.shared.ui.auth.CodigoSegundoFator
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme

/** Conta e segurança (issues #183, #261). */
@Composable
fun AccountScreen(onLoggedOut: () -> Unit, onViewTutorial: () -> Unit = {}) {
    val container = LocalAppContainer.current
    val viewModel = viewModel<AccountViewModel>(
        factory = viewModelFactory { initializer { AccountViewModel(container.authApi) } },
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
        onDispensarCodigosRecuperacao = viewModel::dispensarCodigosRecuperacao,
        onAbrirDesativacao = viewModel::abrirDesativacao,
        onCodigoDesativacaoChange = viewModel::onCodigoDesativacaoChange,
        onConfirmarDesativacao = viewModel::confirmarDesativacao,
        onDispensarDesativacao = viewModel::dispensarDesativacao,
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
    onDispensarCodigosRecuperacao: () -> Unit = {},
    onAbrirDesativacao: () -> Unit = {},
    onCodigoDesativacaoChange: (String) -> Unit = {},
    onConfirmarDesativacao: () -> Unit = {},
    onDispensarDesativacao: () -> Unit = {},
) {
    val clipboard = LocalClipboardManager.current
    val algumDialogoAberto = state.showConfigDialog || state.showDesativarDialog
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
        if (state.mfaHabilitado == true) {
            val restantes = state.codigosRecuperacaoRestantes
            Text(
                when {
                    restantes == 0 -> "Você não tem mais códigos de recuperação. Reconfigure o 2FA para gerar novos."
                    restantes <= 2 -> "Restam só $restantes códigos de recuperação. Reconfigure o 2FA para gerar novos."
                    else -> "$restantes códigos de recuperação disponíveis."
                },
                style = CafeyTheme.typography.body,
                color = if (restantes <= 2) CafeyTheme.colors.brandDeep else CafeyTheme.colors.muted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        state.mfaSuccessMessage?.let {
            Text(it, color = CafeyTheme.colors.brand, modifier = Modifier.padding(top = 8.dp))
        }
        state.mfaErrorMessage?.takeIf { !algumDialogoAberto }?.let {
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
        if (state.mfaHabilitado == true) {
            OutlinedButton(
                onClick = onAbrirDesativacao,
                shape = CafeyTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text("Desativar autenticação em duas etapas")
            }
        }

        Button(
            onClick = onLogout,
            colors = ButtonDefaults.buttonColors(containerColor = CafeyTheme.colors.brand),
            shape = CafeyTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        ) {
            Text("Sair", color = CafeyTheme.colors.brandOn)
        }
        OutlinedButton(
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
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.mfaHabilitado == true) {
                        Text(
                            "Seu autenticador atual continua valendo até você confirmar o novo código. " +
                                "Se cancelar, nada muda.",
                            color = CafeyTheme.colors.muted,
                        )
                    }
                    Text("Escaneie o QR code com seu app autenticador (Google Authenticator, Authy etc.).")
                    state.configOtpAuthUri?.let { uri ->
                        QrCode(
                            conteudo = uri,
                            descricao = "QR code para configurar o app autenticador",
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                    Text("Se estiver no mesmo celular do autenticador, copie a chave e cole no app:")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            state.configSegredo,
                            modifier = Modifier.weight(1f),
                            style = CafeyTheme.typography.body,
                            fontFamily = FontFamily.Monospace,
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
                        Text(if (state.mfaHabilitado == true) "Confirmar" else "Ativar")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDispensarDialogo) { Text("Cancelar") }
            },
        )
    }

    state.codigosRecuperacao?.let { codigos ->
        AlertDialog(
            // Sem fechar ao tocar fora: os códigos não aparecem de novo.
            onDismissRequest = {},
            title = { Text("Guarde seus códigos de recuperação") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Se perder o celular, use um destes códigos no lugar do app autenticador. " +
                            "Cada um funciona uma vez. Guarde num gerenciador de senhas ou anote: " +
                            "eles não serão mostrados de novo.",
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        codigos.forEach { codigo ->
                            Text(codigo, fontFamily = FontFamily.Monospace, style = CafeyTheme.typography.body)
                        }
                    }
                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(codigos.joinToString("\n"))) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Copiar todos")
                    }
                }
            },
            confirmButton = {
                Button(onClick = onDispensarCodigosRecuperacao) { Text("Já guardei") }
            },
        )
    }

    if (state.showDesativarDialog) {
        AlertDialog(
            onDismissRequest = onDispensarDesativacao,
            title = { Text("Desativar autenticação em duas etapas") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Para confirmar, digite o código de 6 dígitos do app autenticador " +
                            "ou um código de recuperação.",
                    )
                    OutlinedTextField(
                        value = state.codigoDesativacao,
                        onValueChange = onCodigoDesativacaoChange,
                        label = { Text("Código") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        isError = state.mfaErrorMessage != null,
                        supportingText = { state.mfaErrorMessage?.let { Text(it) } },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CafeyTheme.colors.brand),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onConfirmarDesativacao,
                    enabled = !state.mfaDeactivateLoading && CodigoSegundoFator.isValido(state.codigoDesativacao),
                ) {
                    if (state.mfaDeactivateLoading) {
                        CircularProgressIndicator(color = CafeyTheme.colors.brandOn)
                    } else {
                        Text("Desativar")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDispensarDesativacao) { Text("Cancelar") }
            },
        )
    }
}
