package br.com.tavaressan.cafey.shared.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.tavaressan.cafey.shared.domain.model.AtualizarDispositivoRequest
import br.com.tavaressan.cafey.shared.domain.model.CriarDispositivoRequest
import br.com.tavaressan.cafey.shared.domain.validation.DeviceFormErrors
import br.com.tavaressan.cafey.shared.network.ApiError
import br.com.tavaressan.cafey.shared.network.DeviceApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Padrões de `Dispositivo` no backend, pré-preenchidos no cadastro.
private const val TIMEZONE_PADRAO = "America/Sao_Paulo"
private const val DURACAO_PADRAO_S = 300
private const val LIMIAR_PADRAO = 200

/** Campos numéricos ficam como texto, do jeito que o usuário digita; a conversão é feita em [DeviceFormViewModel.gravar]. */
data class DeviceFormUiState(
    /** `null` = cadastro de um dispositivo novo; preenchido = edição. */
    val deviceId: String? = null,
    val loading: Boolean = false,
    val nome: String = "",
    val timezone: String = TIMEZONE_PADRAO,
    val duracaoPreparoS: String = DURACAO_PADRAO_S.toString(),
    val limiarDescalcificacao: String = LIMIAR_PADRAO.toString(),
    val errors: DeviceFormErrors = DeviceFormErrors(),
    val saving: Boolean = false,
    val errorMessage: String? = null,
    val success: Boolean = false,
)

/** Formulário de dispositivo: cadastro (APP-13), edição e exclusão. */
class DeviceFormViewModel(
    private val deviceApi: DeviceApi,
    deviceId: String? = null,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope
    private val _uiState = MutableStateFlow(DeviceFormUiState(deviceId = deviceId, loading = deviceId != null))
    val uiState: StateFlow<DeviceFormUiState> = _uiState.asStateFlow()

    init {
        if (deviceId != null) scope.launch { carregar(deviceId) }
    }

    private suspend fun carregar(deviceId: String) {
        try {
            val dispositivo = deviceApi.obter(deviceId)
            _uiState.update {
                it.copy(
                    loading = false,
                    nome = dispositivo.nome,
                    timezone = dispositivo.timezone,
                    duracaoPreparoS = dispositivo.duracaoPreparoS.toString(),
                    limiarDescalcificacao = dispositivo.limiarDescalcificacao.toString(),
                )
            }
        } catch (e: ApiError) {
            _uiState.update { it.copy(loading = false, errorMessage = e.message) }
        }
    }

    fun onNomeChange(value: String) {
        _uiState.update { it.copy(nome = value, errors = it.errors.copy(nome = null), errorMessage = null) }
    }

    fun onTimezoneChange(value: String) {
        _uiState.update { it.copy(timezone = value, errorMessage = null) }
    }

    fun onDuracaoPreparoSChange(value: String) {
        _uiState.update { it.copy(duracaoPreparoS = value, errors = it.errors.copy(duracaoPreparoS = null), errorMessage = null) }
    }

    fun onLimiarDescalcificacaoChange(value: String) {
        _uiState.update {
            it.copy(limiarDescalcificacao = value, errors = it.errors.copy(limiarDescalcificacao = null), errorMessage = null)
        }
    }

    /** Botão "Limpar Campos": esvazia o formulário sem sair dele. Na edição, continua editando o mesmo dispositivo. */
    fun limparCampos() {
        _uiState.update {
            it.copy(
                nome = "",
                timezone = "",
                duracaoPreparoS = "",
                limiarDescalcificacao = "",
                errors = DeviceFormErrors(),
                errorMessage = null,
            )
        }
    }

    /** Botão "Gravar": cadastra ou atualiza, conforme [DeviceFormUiState.deviceId]. */
    fun gravar() {
        val state = _uiState.value
        val errors = DeviceFormErrors.validate(state.nome, state.duracaoPreparoS, state.limiarDescalcificacao)
        _uiState.update { it.copy(errors = errors) }
        if (!errors.isValid) return

        // Campo opcional vazio vai como null: o backend usa o padrão no cadastro e mantém o valor atual na edição.
        val timezone = state.timezone.trim().ifEmpty { null }
        val duracaoPreparoS = state.duracaoPreparoS.trim().toIntOrNull()
        val limiarDescalcificacao = state.limiarDescalcificacao.trim().toIntOrNull()

        _uiState.update { it.copy(saving = true, errorMessage = null) }
        scope.launch {
            try {
                if (state.deviceId == null) {
                    deviceApi.criar(CriarDispositivoRequest(state.nome, timezone, duracaoPreparoS, limiarDescalcificacao))
                } else {
                    deviceApi.atualizar(
                        state.deviceId,
                        AtualizarDispositivoRequest(state.nome, timezone, duracaoPreparoS, limiarDescalcificacao),
                    )
                }
                _uiState.update { it.copy(saving = false, success = true) }
            } catch (e: ApiError) {
                _uiState.update { it.copy(saving = false, errorMessage = e.message) }
            }
        }
    }

    fun excluir() {
        val deviceId = _uiState.value.deviceId ?: return
        _uiState.update { it.copy(saving = true, errorMessage = null) }
        scope.launch {
            try {
                deviceApi.excluir(deviceId)
                _uiState.update { it.copy(saving = false, success = true) }
            } catch (e: ApiError) {
                _uiState.update { it.copy(saving = false, errorMessage = e.message) }
            }
        }
    }
}
