package br.com.tavaressan.cafey.shared.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.tavaressan.cafey.shared.LocalAppContainer
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme
import br.com.tavaressan.cafey.shared.ui.theme.CafeyWordmark

/**
 * Issue #261 — desafio 2FA após login: valida o código TOTP de 6 dígitos (ou um código de recuperação)
 * e emite a sessão. [onBack] volta ao login, inclusive quando o desafio de 5 minutos vence.
 */
@Composable
fun MfaChallengeScreen(
    desafioToken: String,
    onVerified: () -> Unit,
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel = viewModel<MfaChallengeViewModel>(
        key = desafioToken,
        factory = viewModelFactory {
            initializer { MfaChallengeViewModel(container.authApi, desafioToken) }
        },
    )
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.success) {
        if (state.success) onVerified()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CafeyTheme.colors.ground)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        CafeyWordmark(modifier = Modifier.padding(bottom = 24.dp), iconSize = 44.dp)
        Text(
            "Verificação em duas etapas",
            style = CafeyTheme.typography.screenTitle,
            color = CafeyTheme.colors.ink,
        )
        if (state.expirado) {
            Text(
                "O tempo para digitar o código acabou. Entre de novo com seu e-mail e senha.",
                style = CafeyTheme.typography.body,
                color = CafeyTheme.colors.muted,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = CafeyTheme.colors.brand),
                shape = CafeyTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            ) {
                Text("Voltar ao login")
            }
            return@Column
        }

        Text(
            if (state.modoRecuperacao) {
                "Digite um dos códigos de recuperação que você guardou ao ativar o 2FA. Cada código funciona uma vez."
            } else {
                "Digite o código de 6 dígitos do seu app autenticador."
            },
            style = CafeyTheme.typography.body,
            color = CafeyTheme.colors.muted,
            modifier = Modifier.padding(top = 8.dp),
        )

        OutlinedTextField(
            value = state.codigo,
            onValueChange = viewModel::onCodigoChange,
            label = { Text(if (state.modoRecuperacao) "Código de recuperação" else "Código") },
            placeholder = if (state.modoRecuperacao) ({ Text("XXXXX-XXXXX") }) else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (state.modoRecuperacao) KeyboardType.Ascii else KeyboardType.Number,
            ),
            isError = state.errorMessage != null,
            supportingText = { state.errorMessage?.let { Text(it) } },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CafeyTheme.colors.brand),
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        )

        Button(
            onClick = viewModel::submit,
            enabled = viewModel.podeEnviar(state),
            colors = ButtonDefaults.buttonColors(containerColor = CafeyTheme.colors.brand),
            shape = CafeyTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = CafeyTheme.colors.brandOn)
            } else {
                Text("Verificar")
            }
        }

        TextButton(
            onClick = viewModel::alternarModoRecuperacao,
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text(
                if (state.modoRecuperacao) "Usar o app autenticador" else "Perdeu o celular? Usar código de recuperação",
                color = CafeyTheme.colors.brand,
            )
        }
        TextButton(
            onClick = onBack,
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Voltar ao login", color = CafeyTheme.colors.muted)
        }
    }
}
