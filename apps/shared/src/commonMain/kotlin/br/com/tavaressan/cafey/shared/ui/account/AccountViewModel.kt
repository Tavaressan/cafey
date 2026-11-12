package br.com.tavaressan.cafey.shared.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.tavaressan.cafey.shared.network.ApiError
import br.com.tavaressan.cafey.shared.network.AuthApi
import br.com.tavaressan.cafey.shared.push.PushRegistrationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountUiState(
    val loggedOut: Boolean = false,
    val mfaHabilitado: Boolean? = null,
    val mfaStatusLoading: Boolean = false,
    val mfaConfigLoading: Boolean = false,
    val mfaActivateLoading: Boolean = false,
    val configSegredo: String? = null,
    val configOtpAuthUri: String? = null,
    val showConfigDialog: Boolean = false,
    val codigoAtivacao: String = "",
    val mfaErrorMessage: String? = null,
    val mfaSuccessMessage: String? = null,
)

/**
 * Conta + segurança (issues #183, #261, #364/#365). Navegação é efeito colateral na tela (LaunchedEffect).
 */
class AccountViewModel(
    private val authApi: AuthApi,
    private val pushRegistrationManager: PushRegistrationManager? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    init {
        carregarStatusMfa()
    }

    fun logout() {
        viewModelScope.launch {
            // Remove o destino de push antes de apagar o JWT (contrato da API de push).
            try {
                pushRegistrationManager?.removerAntesDeLogout()
            } catch (_: Throwable) {
                // Falha de rede no logout não pode quebrar a saída da conta.
            }
            authApi.logout()
            _uiState.update { it.copy(loggedOut = true) }
        }
    }

    fun carregarStatusMfa() {
        _uiState.update { it.copy(mfaStatusLoading = true, mfaErrorMessage = null) }
        viewModelScope.launch {
            try {
                val status = authApi.status2fa()
                _uiState.update { it.copy(mfaStatusLoading = false, mfaHabilitado = status.mfaHabilitado) }
            } catch (e: ApiError) {
                _uiState.update {
                    it.copy(mfaStatusLoading = false, mfaErrorMessage = mensagemMfaErro(e))
                }
            }
        }
    }

    fun iniciarConfiguracao() {
        _uiState.update { it.copy(mfaConfigLoading = true, mfaErrorMessage = null, mfaSuccessMessage = null) }
        viewModelScope.launch {
            try {
                val cfg = authApi.configurar2fa()
                _uiState.update {
                    it.copy(
                        mfaConfigLoading = false,
                        configSegredo = cfg.segredo,
                        configOtpAuthUri = cfg.otpauthUri,
                        showConfigDialog = true,
                        codigoAtivacao = "",
                    )
                }
            } catch (e: ApiError) {
                _uiState.update {
                    it.copy(mfaConfigLoading = false, mfaErrorMessage = mensagemMfaErro(e))
                }
            }
        }
    }

    fun onCodigoAtivacaoChange(value: String) {
        val digits = value.filter { it.isDigit() }.take(6)
        _uiState.update { it.copy(codigoAtivacao = digits, mfaErrorMessage = null) }
    }

    fun confirmarAtivacao() {
        val codigo = _uiState.value.codigoAtivacao
        if (codigo.length != 6 || !codigo.all { it.isDigit() } || _uiState.value.mfaActivateLoading) return
        _uiState.update { it.copy(mfaActivateLoading = true, mfaErrorMessage = null) }
        viewModelScope.launch {
            try {
                authApi.ativar2fa(codigo)
                _uiState.update {
                    it.copy(
                        mfaActivateLoading = false,
                        showConfigDialog = false,
                        configSegredo = null,
                        configOtpAuthUri = null,
                        codigoAtivacao = "",
                        mfaHabilitado = true,
                        mfaSuccessMessage = "Autenticação em duas etapas ativada.",
                    )
                }
            } catch (e: ApiError) {
                _uiState.update {
                    it.copy(mfaActivateLoading = false, mfaErrorMessage = mensagemMfaErro(e))
                }
            }
        }
    }

    fun dispensarDialogoConfiguracao() {
        _uiState.update {
            it.copy(showConfigDialog = false, codigoAtivacao = "", mfaErrorMessage = null)
        }
    }

    private fun mensagemMfaErro(e: ApiError): String =
        if (e is ApiError.Http && e.status == 503) {
            "2FA indisponível no servidor"
        } else {
            e.message ?: "Não foi possível concluir a operação"
        }
}
