package br.com.tavaressan.cafey.android.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.tavaressan.cafey.shared.domain.model.RegisterRequest
import br.com.tavaressan.cafey.shared.domain.validation.RegisterFormErrors
import br.com.tavaressan.cafey.shared.network.ApiError
import br.com.tavaressan.cafey.shared.network.AuthApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val nome: String = "",
    val email: String = "",
    val senha: String = "",
    val errors: RegisterFormErrors = RegisterFormErrors(),
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val success: Boolean = false,
)

/** UC-01 (cadastrar usuário). Mesma regra de UI fina que `LoginViewModel`. */
class RegisterViewModel(private val authApi: AuthApi) : ViewModel() {
    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onNomeChange(value: String) {
        _uiState.update { it.copy(nome = value, errorMessage = null) }
    }

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, errorMessage = null) }
    }

    fun onSenhaChange(value: String) {
        _uiState.update { it.copy(senha = value, errorMessage = null) }
    }

    fun submit() {
        val state = _uiState.value
        val errors = RegisterFormErrors.validate(state.nome, state.email, state.senha)
        _uiState.update { it.copy(errors = errors) }
        if (!errors.isValid) return

        _uiState.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                authApi.registrar(RegisterRequest(nome = state.nome, email = state.email, senha = state.senha))
                _uiState.update { it.copy(loading = false, success = true) }
            } catch (e: ApiError) {
                _uiState.update { it.copy(loading = false, errorMessage = e.message) }
            }
        }
    }
}
