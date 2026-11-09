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

/** Issue #261 — desafio 2FA após login: valida o código TOTP de 6 dígitos e emite a sessão. */
@Composable
fun MfaChallengeScreen(
    desafioToken: String,
    onVerified: () -> Unit,
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
        Text(
            "Digite o código de 6 dígitos do seu app autenticador.",
            style = CafeyTheme.typography.body,
            color = CafeyTheme.colors.muted,
            modifier = Modifier.padding(top = 8.dp),
        )

        OutlinedTextField(
            value = state.codigo,
            onValueChange = viewModel::onCodigoChange,
            label = { Text("Código") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = state.errorMessage != null,
            supportingText = { state.errorMessage?.let { Text(it) } },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CafeyTheme.colors.brand),
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        )

        Button(
            onClick = viewModel::submit,
            enabled = !state.loading && MfaChallengeViewModel.isCodigoValido(state.codigo),
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
    }
}
