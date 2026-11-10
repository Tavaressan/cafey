package br.com.tavaressan.cafey.shared.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.tavaressan.cafey.shared.domain.model.LoginRequest
import br.com.tavaressan.cafey.shared.domain.validation.LoginFormErrors
import br.com.tavaressan.cafey.shared.network.ApiError
import br.com.tavaressan.cafey.shared.network.AuthApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val senha: String = "",
    val errors: LoginFormErrors = LoginFormErrors(),
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val success: Boolean = false,
    val mfaChallengeToken: String? = null,
)

/** UC-02 (autenticar). A regra de validação vem de `shared` (`LoginFormErrors`) — este ViewModel
 * só orquestra estado de UI e a chamada de rede. */
class LoginViewModel(private val authApi: AuthApi) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, errorMessage = null) }
    }

    fun onSenhaChange(value: String) {
        _uiState.update { it.copy(senha = value, errorMessage = null) }
    }

    fun submit() {
        val state = _uiState.value
        val errors = LoginFormErrors.validate(state.email, state.senha)
        _uiState.update { it.copy(errors = errors) }
        if (!errors.isValid) return

        _uiState.update { it.copy(loading = true, errorMessage = null, mfaChallengeToken = null) }
        viewModelScope.launch {
            try {
                val response = authApi.login(LoginRequest(email = state.email, senha = state.senha))
                val desafio = response.desafioToken
                if (response.mfaRequerido == true && !desafio.isNullOrBlank()) {
                    _uiState.update { it.copy(loading = false, mfaChallengeToken = desafio) }
                } else {
                    _uiState.update { it.copy(loading = false, success = true) }
                }
            } catch (e: ApiError) {
                val message = if (e is ApiError.Http && e.status == 503) {
                    "2FA indisponível no servidor"
                } else {
                    e.message ?: "Falha ao entrar"
                }
                _uiState.update { it.copy(loading = false, errorMessage = message) }
            }
        }
    }

    fun consumeMfaChallenge() {
        _uiState.update { it.copy(mfaChallengeToken = null) }
    }
}
