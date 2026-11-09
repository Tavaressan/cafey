package br.com.tavaressan.cafey.shared.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.tavaressan.cafey.shared.ui.ble.BlePermissionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Lógica do tutorial de primeira utilização (issue #312): navegação entre etapas, pular/concluir
 * com persistência, e estado de BLE/permissões da etapa 3 reportado pela plataforma.
 */
class OnboardingViewModel(
    private val markCompleted: suspend () -> Unit,
    revisit: Boolean = false,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(OnboardingUiState(revisit = revisit))
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun next() {
        _uiState.update { state ->
            if (state.canAdvance) state.copy(step = OnboardingStep.fromIndex(state.step.index + 1)) else state
        }
    }

    fun back() {
        _uiState.update { state ->
            if (state.canGoBack) state.copy(step = OnboardingStep.fromIndex(state.step.index - 1)) else state
        }
    }

    fun goToStep(step: OnboardingStep) {
        _uiState.update { it.copy(step = step) }
    }

    /** Pular o tutorial: persiste conclusão (não mostrar de novo) e sinaliza saída. */
    fun skip() = finishInternal(OnboardingExit.SKIPPED)

    /** Concluir o tutorial: persiste conclusão e sinaliza saída. */
    fun complete() = finishInternal(OnboardingExit.COMPLETED)

    /** Fechar quando aberto explicitamente por "Ver tutorial". Persiste como concluído por segurança. */
    fun dismiss() = finishInternal(if (_uiState.value.revisit) OnboardingExit.DISMISSED else OnboardingExit.SKIPPED)

    private fun finishInternal(exit: OnboardingExit) {
        var accepted = false
        _uiState.update { state ->
            if (state.finished) state else {
                accepted = true
                state.copy(finished = true, exit = exit)
            }
        }
        if (!accepted) return
        scope.launch { markCompleted() }
    }

    /** Atualiza estado BLE/permissões reportado pela plataforma (etapa 3). */
    fun updateBleStatus(status: BlePermissionStatus) {
        _uiState.update { it.copy(bleStatus = status) }
    }

    /** Indica se o usuário pode seguir para provisionar na etapa 3 (permissões OK e BT ligado). */
    fun canProvisionFromStep3(state: OnboardingUiState = _uiState.value): Boolean = state.bleStatus.ready
}
