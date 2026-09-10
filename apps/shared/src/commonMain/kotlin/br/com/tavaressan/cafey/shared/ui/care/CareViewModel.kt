package br.com.tavaressan.cafey.shared.ui.care

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.tavaressan.cafey.shared.domain.model.PapelDispositivo
import br.com.tavaressan.cafey.shared.domain.model.StatusDescalcificacaoResponse
import br.com.tavaressan.cafey.shared.network.ApiError
import br.com.tavaressan.cafey.shared.network.DeviceApi
import br.com.tavaressan.cafey.shared.network.EventApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CareUiState(
    val loading: Boolean = true,
    val deviceId: String? = null,
    // Só o proprietário pode dar baixa (regra do backend, EventoService.darBaixaDescalcificacao).
    val podeDarBaixa: Boolean = false,
    val status: StatusDescalcificacaoResponse? = null,
    val baixaInFlight: Boolean = false,
    val errorMessage: String? = null,
)

/** UC-16/17 — alerta de descalcificação e baixa do contador (APP-07). */
class CareViewModel(
    private val deviceApi: DeviceApi,
    private val eventApi: EventApi,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CareUiState())
    val uiState: StateFlow<CareUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        try {
            // MVP de um único dispositivo, mesma simplificação de HomeViewModel (APP-04).
            val device = deviceApi.listar().firstOrNull()
            if (device == null) {
                _uiState.update { it.copy(loading = false) }
                return
            }
            val status = eventApi.obterStatusDescalcificacao(device.id)
            _uiState.update {
                it.copy(
                    loading = false,
                    deviceId = device.id,
                    podeDarBaixa = device.papel == PapelDispositivo.PROPRIETARIO,
                    status = status,
                    errorMessage = null,
                )
            }
        } catch (e: ApiError) {
            _uiState.update { it.copy(loading = false, errorMessage = e.message) }
        }
    }

    /** UC-17 — "Já fiz isso": zera o contador de descalcificação. */
    fun darBaixa() {
        val deviceId = _uiState.value.deviceId ?: return
        _uiState.update { it.copy(baixaInFlight = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val status = eventApi.darBaixaDescalcificacao(deviceId)
                _uiState.update { it.copy(baixaInFlight = false, status = status) }
            } catch (e: ApiError) {
                _uiState.update { it.copy(baixaInFlight = false, errorMessage = e.message) }
            }
        }
    }
}
