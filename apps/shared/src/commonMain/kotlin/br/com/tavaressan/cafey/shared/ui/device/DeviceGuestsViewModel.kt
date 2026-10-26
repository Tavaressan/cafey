package br.com.tavaressan.cafey.shared.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.tavaressan.cafey.shared.domain.model.CompartilhamentoResponse
import br.com.tavaressan.cafey.shared.domain.model.CompartilharDispositivoRequest
import br.com.tavaressan.cafey.shared.domain.validation.AuthValidation
import br.com.tavaressan.cafey.shared.domain.validation.FieldError
import br.com.tavaressan.cafey.shared.network.ApiError
import br.com.tavaressan.cafey.shared.network.DeviceApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DeviceGuestsUiState(
    val loading: Boolean = false,
    val guests: List<CompartilhamentoResponse> = emptyList(),
    val emailInput: String = "",
    val emailError: FieldError? = null,
    val isInviting: Boolean = false,
    val isRemoving: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

/**
 * Gerenciamento e compartilhamento de dispositivo com convidados na UI (UC-05).
 */
class DeviceGuestsViewModel(
    val deviceId: String,
    private val deviceApi: DeviceApi,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(DeviceGuestsUiState(loading = true))
    val uiState: StateFlow<DeviceGuestsUiState> = _uiState.asStateFlow()

    init {
        loadGuests()
    }

    fun loadGuests() {
        _uiState.update { it.copy(loading = true, errorMessage = null) }
        scope.launch {
            try {
                val list = deviceApi.listarCompartilhamentos(deviceId)
                _uiState.update { it.copy(loading = false, guests = list) }
            } catch (e: ApiError) {
                _uiState.update { it.copy(loading = false, errorMessage = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, errorMessage = e.message ?: "Falha ao carregar convidados") }
            }
        }
    }

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(emailInput = value, emailError = null, errorMessage = null) }
    }

    fun inviteGuest() {
        val email = _uiState.value.emailInput.trim()
        val error = AuthValidation.validateEmail(email)
        if (error != null) {
            _uiState.update { it.copy(emailError = error) }
            return
        }

        _uiState.update { it.copy(isInviting = true, errorMessage = null, successMessage = null) }
        scope.launch {
            try {
                val response = deviceApi.compartilhar(deviceId, CompartilharDispositivoRequest(email = email))
                val current = _uiState.value.guests
                val updated = if (current.none { it.usuarioId == response.usuarioId }) current + response else current
                _uiState.update {
                    it.copy(
                        isInviting = false,
                        guests = updated,
                        emailInput = "",
                        successMessage = "Convite enviado para $email com sucesso!",
                    )
                }
            } catch (e: ApiError) {
                _uiState.update { it.copy(isInviting = false, errorMessage = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isInviting = false, errorMessage = e.message ?: "Falha ao convidar usuário") }
            }
        }
    }

    fun removeGuest(convidadoId: String) {
        _uiState.update { it.copy(isRemoving = true, errorMessage = null, successMessage = null) }
        scope.launch {
            try {
                deviceApi.removerCompartilhamento(deviceId, convidadoId)
                val updated = _uiState.value.guests.filterNot { it.usuarioId == convidadoId }
                _uiState.update {
                    it.copy(
                        isRemoving = false,
                        guests = updated,
                        successMessage = "Vínculo do convidado removido com sucesso!",
                    )
                }
            } catch (e: ApiError) {
                _uiState.update { it.copy(isRemoving = false, errorMessage = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRemoving = false, errorMessage = e.message ?: "Falha ao remover convidado") }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
