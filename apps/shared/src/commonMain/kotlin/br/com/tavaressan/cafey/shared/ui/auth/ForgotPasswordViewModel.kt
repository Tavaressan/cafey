package br.com.tavaressan.cafey.shared.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.tavaressan.cafey.shared.domain.validation.AuthValidation
import br.com.tavaressan.cafey.shared.domain.validation.FieldError
import br.com.tavaressan.cafey.shared.network.ApiError
import br.com.tavaressan.cafey.shared.network.AuthApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ForgotPasswordUiState(
    val email: String = "",
    val emailError: FieldError? = null,
    val loading: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null,
)

class ForgotPasswordViewModel(
    private val authApi: AuthApi,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, emailError = null, errorMessage = null) }
    }

    fun submit() {
        val email = _uiState.value.email.trim()
        val error = AuthValidation.validateEmail(email)
        if (error != null) {
            _uiState.update { it.copy(emailError = error) }
            return
        }

        _uiState.update { it.copy(loading = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            try {
                val response = authApi.solicitarRecuperacaoSenha(email)
                _uiState.update { it.copy(loading = false, successMessage = response.mensagem) }
            } catch (e: ApiError) {
                _uiState.update { it.copy(loading = false, errorMessage = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, errorMessage = "Erro inesperado. Tente novamente.") }
            }
        }
    }
}
