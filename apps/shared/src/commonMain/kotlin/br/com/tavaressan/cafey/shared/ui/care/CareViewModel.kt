package br.com.tavaressan.cafey.shared.ui.care

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.tavaressan.cafey.shared.domain.model.CuidadosResponse
import br.com.tavaressan.cafey.shared.domain.model.PapelDispositivo
import br.com.tavaressan.cafey.shared.domain.model.TipoCuidado
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
    // Só o proprietário pode dar baixa (regra do backend, EventoService.vinculoDoProprietario).
    val podeDarBaixa: Boolean = false,
    val cuidados: CuidadosResponse? = null,
    // Cuidado cuja baixa está em andamento; enquanto houver um, os botões ficam desabilitados.
    val baixaInFlight: TipoCuidado? = null,
    val errorMessage: String? = null,
)

/** UC-16/17 — alertas e baixa dos cuidados: enxágue, filtro e descalcificação (APP-07, issue #190). */
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
            val cuidados = eventApi.obterCuidados(device.id)
            _uiState.update {
                it.copy(
                    loading = false,
                    deviceId = device.id,
                    podeDarBaixa = device.papel == PapelDispositivo.PROPRIETARIO,
                    cuidados = cuidados,
                    errorMessage = null,
                )
            }
        } catch (e: ApiError) {
            _uiState.update { it.copy(loading = false, errorMessage = e.message) }
        }
    }

    /** UC-17 — "Já fiz isso": zera o contador do cuidado e recarrega os três, porque a baixa pode
     * mudar qual deles é o destaque. */
    fun darBaixa(tipo: TipoCuidado) {
        val deviceId = _uiState.value.deviceId ?: return
        _uiState.update { it.copy(baixaInFlight = tipo, errorMessage = null) }
        viewModelScope.launch {
            try {
                when (tipo) {
                    TipoCuidado.ENXAGUE -> eventApi.darBaixaEnxague(deviceId)
                    TipoCuidado.FILTRO -> eventApi.darBaixaFiltro(deviceId)
                    TipoCuidado.DESCALCIFICACAO -> eventApi.darBaixaDescalcificacao(deviceId)
                }
                val cuidados = eventApi.obterCuidados(deviceId)
                _uiState.update { it.copy(baixaInFlight = null, cuidados = cuidados) }
            } catch (e: ApiError) {
                _uiState.update { it.copy(baixaInFlight = null, errorMessage = e.message) }
            }
        }
    }
}
