package br.com.tavaressan.cafey.android.ui.auth

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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.tavaressan.cafey.shared.LocalAppContainer
import br.com.tavaressan.cafey.shared.domain.validation.FieldError
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme

internal fun fieldErrorMessage(error: FieldError?): String? = when (error) {
    null -> null
    FieldError.Required -> "Campo obrigatório"
    FieldError.InvalidEmail -> "Email inválido"
    FieldError.PasswordTooShort -> "Senha deve ter no mínimo 6 caracteres"
}

/** UC-02 — tela de login. */
@Composable
fun LoginScreen(onLoggedIn: () -> Unit, onGoToRegister: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = viewModel<LoginViewModel>(
        factory = viewModelFactory { initializer { LoginViewModel(container.authApi) } },
    )
    val state by viewModel.uiState.collectAsState()

    if (state.success) {
        onLoggedIn()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CafeyTheme.colors.ground)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Entrar", style = CafeyTheme.typography.screenTitle, color = CafeyTheme.colors.ink)

        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = { Text("Email") },
            isError = state.errors.email != null,
            supportingText = { fieldErrorMessage(state.errors.email)?.let { Text(it) } },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CafeyTheme.colors.brand),
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        )

        OutlinedTextField(
            value = state.senha,
            onValueChange = viewModel::onSenhaChange,
            label = { Text("Senha") },
            visualTransformation = PasswordVisualTransformation(),
            isError = state.errors.senha != null,
            supportingText = { fieldErrorMessage(state.errors.senha)?.let { Text(it) } },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CafeyTheme.colors.brand),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )

        state.errorMessage?.let {
            Text(it, color = CafeyTheme.colors.brandDeep, modifier = Modifier.padding(top = 12.dp))
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
                Text("Entrar")
            }
        }

        TextButton(onClick = onGoToRegister, modifier = Modifier.padding(top = 8.dp)) {
            Text("Não tem conta? Cadastre-se", color = CafeyTheme.colors.blueDeep)
        }
    }
}
