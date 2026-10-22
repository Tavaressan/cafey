package br.com.tavaressan.cafey.shared.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.tavaressan.cafey.shared.domain.validation.ResetPasswordFormErrors
import br.com.tavaressan.cafey.shared.network.ApiError
import br.com.tavaressan.cafey.shared.network.AuthApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ResetPasswordUiState(
    val token: String = "",
    val novaSenha: String = "",
    val confirmacaoSenha: String = "",
    val errors: ResetPasswordFormErrors = ResetPasswordFormErrors(),
    val loading: Boolean = false,
    val success: Boolean = false,
    val errorMessage: String? = null,
)

class ResetPasswordViewModel(
    private val authApi: AuthApi,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ResetPasswordUiState())
    val uiState: StateFlow<ResetPasswordUiState> = _uiState.asStateFlow()

    fun onTokenChange(token: String) {
        _uiState.update { it.copy(token = token, errors = it.errors.copy(token = null), errorMessage = null) }
    }

    fun onNovaSenhaChange(novaSenha: String) {
        _uiState.update { it.copy(novaSenha = novaSenha, errors = it.errors.copy(novaSenha = null), errorMessage = null) }
    }

    fun onConfirmacaoSenhaChange(confirmacaoSenha: String) {
        _uiState.update { it.copy(confirmacaoSenha = confirmacaoSenha, errors = it.errors.copy(confirmacaoSenha = null), errorMessage = null) }
    }

    fun submit() {
        val current = _uiState.value
        val errors = ResetPasswordFormErrors.validate(
            token = current.token.trim(),
            novaSenha = current.novaSenha,
            confirmacaoSenha = current.confirmacaoSenha,
        )

        if (!errors.isValid) {
            _uiState.update { it.copy(errors = errors) }
            return
        }

        _uiState.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                authApi.redefinirSenha(current.token.trim(), current.novaSenha)
                _uiState.update { it.copy(loading = false, success = true) }
            } catch (e: ApiError) {
                _uiState.update { it.copy(loading = false, errorMessage = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, errorMessage = "Erro inesperado. Tente novamente.") }
            }
        }
    }
}
