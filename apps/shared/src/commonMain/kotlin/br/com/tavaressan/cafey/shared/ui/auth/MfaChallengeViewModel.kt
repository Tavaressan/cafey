package br.com.tavaressan.cafey.shared.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.tavaressan.cafey.shared.network.ApiError
import br.com.tavaressan.cafey.shared.network.AuthApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MfaChallengeUiState(
    val codigo: String = "",
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val success: Boolean = false,
)

class MfaChallengeViewModel(
    private val authApi: AuthApi,
    private val desafioToken: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MfaChallengeUiState())
    val uiState: StateFlow<MfaChallengeUiState> = _uiState.asStateFlow()

    fun onCodigoChange(value: String) {
        val digits = value.filter { it.isDigit() }.take(6)
        _uiState.update { it.copy(codigo = digits, errorMessage = null) }
        if (digits.length == 6) {
            submit()
        }
    }

    fun submit() {
        val state = _uiState.value
        val codigo = state.codigo
        if (!isCodigoValido(codigo) || state.loading) return

        _uiState.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                authApi.validar2fa(desafioToken, codigo)
                _uiState.update { it.copy(loading = false, success = true) }
            } catch (e: ApiError) {
                val message = if (e is ApiError.Http && e.status == 503) {
                    "2FA indisponível no servidor"
                } else {
                    e.message ?: "Código inválido"
                }
                _uiState.update { it.copy(loading = false, errorMessage = message) }
            }
        }
    }

    companion object {
        fun isCodigoValido(codigo: String): Boolean = codigo.length == 6 && codigo.all { it.isDigit() }
    }
}
