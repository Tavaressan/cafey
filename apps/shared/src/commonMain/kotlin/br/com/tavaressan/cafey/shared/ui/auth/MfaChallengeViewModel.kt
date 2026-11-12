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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

data class MfaChallengeUiState(
    val codigo: String = "",
    /** true: o campo aceita um código de recuperação em vez dos 6 dígitos do autenticador. */
    val modoRecuperacao: Boolean = false,
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val success: Boolean = false,
    /** O desafio de login venceu: só resta voltar e entrar de novo. */
    val expirado: Boolean = false,
)

class MfaChallengeViewModel(
    private val authApi: AuthApi,
    private val desafioToken: String,
    timeSource: TimeSource = TimeSource.Monotonic,
    /** Validade do desafio emitido pelo backend (`expiresIn` = 300 s). */
    private val validade: Duration = 5.minutes,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MfaChallengeUiState())
    val uiState: StateFlow<MfaChallengeUiState> = _uiState.asStateFlow()
    private val inicio = timeSource.markNow()

    fun onCodigoChange(value: String) {
        val modoRecuperacao = _uiState.value.modoRecuperacao
        val codigo = if (modoRecuperacao) CodigoSegundoFator.filtrarQualquer(value) else CodigoSegundoFator.filtrarTotp(value)
        _uiState.update { it.copy(codigo = codigo, errorMessage = null) }
        // Os 6 dígitos do autenticador enviam sozinhos; o código de recuperação espera o botão.
        if (!modoRecuperacao && codigo.length == 6) {
            submit()
        }
    }

    fun alternarModoRecuperacao() {
        _uiState.update { it.copy(modoRecuperacao = !it.modoRecuperacao, codigo = "", errorMessage = null) }
    }

    fun podeEnviar(state: MfaChallengeUiState = _uiState.value): Boolean =
        !state.loading && !state.expirado &&
            if (state.modoRecuperacao) CodigoSegundoFator.isRecuperacao(state.codigo) else isCodigoValido(state.codigo)

    fun submit() {
        val state = _uiState.value
        if (!podeEnviar(state)) return
        if (inicio.elapsedNow() > validade) {
            _uiState.update { it.copy(expirado = true, errorMessage = null) }
            return
        }

        _uiState.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                authApi.validar2fa(desafioToken, state.codigo)
                _uiState.update { it.copy(loading = false, success = true) }
            } catch (e: ApiError) {
                if (e is ApiError.Http && e.status == 401 && e.message.orEmpty().contains("desafio", ignoreCase = true)) {
                    _uiState.update { it.copy(loading = false, expirado = true) }
                    return@launch
                }
                val message = when {
                    e is ApiError.Http && e.status == 503 -> "2FA indisponível no servidor"
                    e is ApiError.Http && e.status == 401 -> "Código inválido"
                    else -> e.message ?: "Código inválido"
                }
                _uiState.update { it.copy(loading = false, errorMessage = message) }
            }
        }
    }

    companion object {
        fun isCodigoValido(codigo: String): Boolean = CodigoSegundoFator.isTotp(codigo)
    }
}
