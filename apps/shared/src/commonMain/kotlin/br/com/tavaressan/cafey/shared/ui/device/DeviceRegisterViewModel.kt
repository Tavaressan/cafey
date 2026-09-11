package br.com.tavaressan.cafey.shared.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.tavaressan.cafey.shared.domain.model.CriarDispositivoRequest
import br.com.tavaressan.cafey.shared.domain.validation.DeviceFormErrors
import br.com.tavaressan.cafey.shared.network.ApiError
import br.com.tavaressan.cafey.shared.network.DeviceApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DeviceRegisterUiState(
    val nome: String = "",
    val errors: DeviceFormErrors = DeviceFormErrors(),
    val saving: Boolean = false,
    val errorMessage: String? = null,
    val success: Boolean = false,
)

/**
 * APP-13 — cadastro manual de dispositivo. Recorte mínimo: só o nome, sem provisionamento
 * BLE/Wi-Fi (depende de APP-11/firmware, fora de escopo aqui).
 */
class DeviceRegisterViewModel(private val deviceApi: DeviceApi) : ViewModel() {
    private val _uiState = MutableStateFlow(DeviceRegisterUiState())
    val uiState: StateFlow<DeviceRegisterUiState> = _uiState.asStateFlow()

    fun onNomeChange(value: String) {
        _uiState.update { it.copy(nome = value, errorMessage = null) }
    }

    fun submit() {
        val state = _uiState.value
        val errors = DeviceFormErrors.validate(state.nome)
        _uiState.update { it.copy(errors = errors) }
        if (!errors.isValid) return

        _uiState.update { it.copy(saving = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                deviceApi.criar(CriarDispositivoRequest(nome = state.nome))
                _uiState.update { it.copy(saving = false, success = true) }
            } catch (e: ApiError) {
                _uiState.update { it.copy(saving = false, errorMessage = e.message) }
            }
        }
    }
}
