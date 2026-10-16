package br.com.tavaressan.cafey.shared.ui.care

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.tavaressan.cafey.shared.domain.model.CuidadosResponse
import br.com.tavaressan.cafey.shared.domain.model.PapelDispositivo
import br.com.tavaressan.cafey.shared.domain.model.TipoCuidado
import br.com.tavaressan.cafey.shared.network.ApiError
import br.com.tavaressan.cafey.shared.network.DeviceApi
import br.com.tavaressan.cafey.shared.network.EventApi
import kotlinx.coroutines.CoroutineScope
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
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope
    private val _uiState = MutableStateFlow(CareUiState())
    val uiState: StateFlow<CareUiState> = _uiState.asStateFlow()

    init {
        scope.launch { load() }
    }

    private suspend fun load() {
        try {
            // MVP de um único dispositivo, mesma simplificação de HomeViewModel (APP-04).
            val device = deviceApi.listar().firstOrNull()
            if (device == null) {
                _uiState.update { it.copy(loading = false, cuidados = null, errorMessage = null) }
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
            _uiState.update { it.copy(loading = false, errorMessage = e.message, cuidados = null) }
        }
    }

    /** Permite recarregar os cuidados quando a carga inicial falhou (issue #220). */
    fun retry() {
        scope.launch {
            _uiState.update { it.copy(loading = true, errorMessage = null) }
            load()
        }
    }

    /** UC-17 — "Já fiz isso": zera o contador do cuidado e recarrega os três, porque a baixa pode
     * mudar qual deles é o destaque (issue #206: chamada de baixa e recarga separadas). */
    fun darBaixa(tipo: TipoCuidado) {
        val deviceId = _uiState.value.deviceId ?: return
        _uiState.update { it.copy(baixaInFlight = tipo, errorMessage = null) }
        scope.launch {
            val baixaSucesso = try {
                when (tipo) {
                    TipoCuidado.ENXAGUE -> {
                        val novo = eventApi.darBaixaEnxague(deviceId)
                        _uiState.update { s -> s.copy(baixaInFlight = null, cuidados = s.cuidados?.copy(enxague = novo)) }
                    }
                    TipoCuidado.FILTRO -> {
                        val novo = eventApi.darBaixaFiltro(deviceId)
                        _uiState.update { s -> s.copy(baixaInFlight = null, cuidados = s.cuidados?.copy(filtro = novo)) }
                    }
                    TipoCuidado.DESCALCIFICACAO -> {
                        val novo = eventApi.darBaixaDescalcificacao(deviceId)
                        _uiState.update { s -> s.copy(baixaInFlight = null, cuidados = s.cuidados?.copy(descalcificacao = novo)) }
                    }
                }
                true
            } catch (e: ApiError) {
                _uiState.update { it.copy(baixaInFlight = null, errorMessage = e.message) }
                false
            }

            if (baixaSucesso) {
                try {
                    val cuidados = eventApi.obterCuidados(deviceId)
                    _uiState.update { it.copy(cuidados = cuidados) }
                } catch (_: ApiError) {
                    _uiState.update { it.copy(errorMessage = "Não foi possível atualizar o resumo de cuidados.") }
                }
            }
        }
    }
}
