package br.com.tavaressan.cafey.shared.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.tavaressan.cafey.shared.network.ApiError
import br.com.tavaressan.cafey.shared.network.AuthApi
import br.com.tavaressan.cafey.shared.ui.auth.CodigoSegundoFator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountUiState(
    val loggedOut: Boolean = false,
    val mfaHabilitado: Boolean? = null,
    val codigosRecuperacaoRestantes: Int = 0,
    val mfaStatusLoading: Boolean = false,
    val mfaConfigLoading: Boolean = false,
    val mfaActivateLoading: Boolean = false,
    val configSegredo: String? = null,
    val configOtpAuthUri: String? = null,
    val showConfigDialog: Boolean = false,
    val codigoAtivacao: String = "",
    /** Códigos recém-gerados na ativação; exibidos uma única vez. */
    val codigosRecuperacao: List<String>? = null,
    val showDesativarDialog: Boolean = false,
    val codigoDesativacao: String = "",
    val mfaDeactivateLoading: Boolean = false,
    val mfaErrorMessage: String? = null,
    val mfaSuccessMessage: String? = null,
)

/**
 * Conta + segurança (issues #183, #261). Navegação é efeito colateral na tela (LaunchedEffect).
 */
class AccountViewModel(private val authApi: AuthApi) : ViewModel() {
    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    init {
        carregarStatusMfa()
    }

    fun logout() {
        viewModelScope.launch {
            authApi.logout()
            _uiState.update { it.copy(loggedOut = true) }
        }
    }

    fun carregarStatusMfa() {
        _uiState.update { it.copy(mfaStatusLoading = true, mfaErrorMessage = null) }
        viewModelScope.launch {
            try {
                val status = authApi.status2fa()
                _uiState.update {
                    it.copy(
                        mfaStatusLoading = false,
                        mfaHabilitado = status.mfaHabilitado,
                        codigosRecuperacaoRestantes = status.codigosRecuperacaoRestantes,
                    )
                }
            } catch (e: ApiError) {
                _uiState.update {
                    it.copy(mfaStatusLoading = false, mfaErrorMessage = mensagemMfaErro(e))
                }
            }
        }
    }

    /**
     * Pede um segredo novo ao backend. Ele fica pendente: se o 2FA já estiver ativo, o autenticador
     * atual continua valendo até [confirmarAtivacao] dar certo, então cancelar é seguro.
     */
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
        _uiState.update { it.copy(codigoAtivacao = CodigoSegundoFator.filtrarTotp(value), mfaErrorMessage = null) }
    }

    fun confirmarAtivacao() {
        val codigo = _uiState.value.codigoAtivacao
        if (!CodigoSegundoFator.isTotp(codigo) || _uiState.value.mfaActivateLoading) return
        val reconfigurando = _uiState.value.mfaHabilitado == true
        _uiState.update { it.copy(mfaActivateLoading = true, mfaErrorMessage = null) }
        viewModelScope.launch {
            try {
                val resposta = authApi.ativar2fa(codigo)
                _uiState.update {
                    it.copy(
                        mfaActivateLoading = false,
                        showConfigDialog = false,
                        configSegredo = null,
                        configOtpAuthUri = null,
                        codigoAtivacao = "",
                        mfaHabilitado = true,
                        codigosRecuperacao = resposta.codigosRecuperacao,
                        codigosRecuperacaoRestantes = resposta.codigosRecuperacao.size,
                        mfaSuccessMessage = if (reconfigurando) {
                            "Autenticação em duas etapas reconfigurada."
                        } else {
                            "Autenticação em duas etapas ativada."
                        },
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

    /** Fecha o diálogo dos códigos de recuperação; eles não podem ser vistos de novo. */
    fun dispensarCodigosRecuperacao() {
        _uiState.update { it.copy(codigosRecuperacao = null) }
    }

    fun abrirDesativacao() {
        _uiState.update {
            it.copy(showDesativarDialog = true, codigoDesativacao = "", mfaErrorMessage = null, mfaSuccessMessage = null)
        }
    }

    fun onCodigoDesativacaoChange(value: String) {
        _uiState.update { it.copy(codigoDesativacao = CodigoSegundoFator.filtrarQualquer(value), mfaErrorMessage = null) }
    }

    fun confirmarDesativacao() {
        val codigo = _uiState.value.codigoDesativacao
        if (!CodigoSegundoFator.isValido(codigo) || _uiState.value.mfaDeactivateLoading) return
        _uiState.update { it.copy(mfaDeactivateLoading = true, mfaErrorMessage = null) }
        viewModelScope.launch {
            try {
                authApi.desativar2fa(codigo)
                _uiState.update {
                    it.copy(
                        mfaDeactivateLoading = false,
                        showDesativarDialog = false,
                        codigoDesativacao = "",
                        mfaHabilitado = false,
                        codigosRecuperacaoRestantes = 0,
                        mfaSuccessMessage = "Autenticação em duas etapas desativada.",
                    )
                }
            } catch (e: ApiError) {
                _uiState.update {
                    it.copy(mfaDeactivateLoading = false, mfaErrorMessage = mensagemMfaErro(e))
                }
            }
        }
    }

    fun dispensarDesativacao() {
        _uiState.update { it.copy(showDesativarDialog = false, codigoDesativacao = "", mfaErrorMessage = null) }
    }

    private fun mensagemMfaErro(e: ApiError): String = when {
        e is ApiError.Http && e.status == 503 -> "2FA indisponível no servidor"
        e is ApiError.Http && e.status == 401 -> "Código inválido"
        else -> e.message ?: "Não foi possível concluir a operação"
    }
}
