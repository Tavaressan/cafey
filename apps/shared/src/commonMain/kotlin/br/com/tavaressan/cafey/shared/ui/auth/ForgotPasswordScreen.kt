package br.com.tavaressan.cafey.shared.ui.auth

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.tavaressan.cafey.shared.LocalAppContainer
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme
import br.com.tavaressan.cafey.shared.ui.theme.CafeyWordmark

/** UC-03 — solicitação de recuperação de senha. */
@Composable
fun ForgotPasswordScreen(
    onGoToReset: () -> Unit,
    onBackToLogin: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel = viewModel<ForgotPasswordViewModel>(
        factory = viewModelFactory { initializer { ForgotPasswordViewModel(container.authApi) } },
    )
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CafeyTheme.colors.ground)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        CafeyWordmark(modifier = Modifier.padding(bottom = 24.dp), iconSize = 44.dp)

        Text("Recuperar senha", style = CafeyTheme.typography.screenTitle, color = CafeyTheme.colors.ink)
        Text(
            "Informe seu e-mail cadastrado para receber as instruções de recuperação.",
            style = CafeyTheme.typography.bodySmall,
            color = CafeyTheme.colors.muted,
            modifier = Modifier.padding(top = 4.dp),
        )

        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = { Text("Email") },
            isError = state.emailError != null,
            supportingText = { fieldErrorMessage(state.emailError)?.let { Text(it) } },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CafeyTheme.colors.brand),
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        )

        state.errorMessage?.let {
            Text(it, color = CafeyTheme.colors.brandDeep, modifier = Modifier.padding(top = 12.dp))
        }

        state.successMessage?.let {
            Text(
                it,
                color = CafeyTheme.colors.blueDeep,
                style = CafeyTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Button(
            onClick = viewModel::submit,
            enabled = !state.loading,
            colors = ButtonDefaults.buttonColors(containerColor = CafeyTheme.colors.brand),
            shape = CafeyTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        ) {
            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = CafeyTheme.colors.brandOn)
            } else {
                Text("Enviar instruções")
            }
        }

        TextButton(onClick = onGoToReset, modifier = Modifier.padding(top = 8.dp)) {
            Text("Já tenho um token de recuperação", color = CafeyTheme.colors.blueDeep)
        }

        TextButton(onClick = onBackToLogin, modifier = Modifier.padding(top = 4.dp)) {
            Text("Voltar ao login", color = CafeyTheme.colors.muted)
        }
    }
}
