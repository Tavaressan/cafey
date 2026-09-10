package br.com.tavaressan.cafey.shared.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.tavaressan.cafey.shared.domain.model.EstatisticasConsumoResponse
import br.com.tavaressan.cafey.shared.domain.model.EventoResponse
import br.com.tavaressan.cafey.shared.network.ApiError
import br.com.tavaressan.cafey.shared.network.DeviceApi
import br.com.tavaressan.cafey.shared.network.EventApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 20

data class HistoryUiState(
    val loading: Boolean = true,
    val deviceId: String? = null,
    val eventos: List<EventoResponse> = emptyList(),
    val estatisticas: EstatisticasConsumoResponse? = null,
    val page: Int = 0,
    val hasMore: Boolean = true,
    val loadingMore: Boolean = false,
    val errorMessage: String? = null,
)

/** UC-14/15 — histórico de preparos em lista e estatísticas de consumo (APP-06). */
class HistoryViewModel(
    private val deviceApi: DeviceApi,
    private val eventApi: EventApi,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                // MVP de um único dispositivo, mesma simplificação de HomeViewModel (APP-04).
                val deviceId = deviceApi.listar().firstOrNull()?.id
                if (deviceId == null) {
                    _uiState.update { it.copy(loading = false) }
                    return@launch
                }
                val estatisticas = eventApi.obterEstatisticas(deviceId)
                val primeiraPagina = eventApi.listarEventos(deviceId, page = 0, size = PAGE_SIZE)
                _uiState.update {
                    it.copy(
                        loading = false,
                        deviceId = deviceId,
                        estatisticas = estatisticas,
                        eventos = primeiraPagina.content,
                        page = primeiraPagina.number,
                        hasMore = !primeiraPagina.last,
                    )
                }
            } catch (e: ApiError) {
                _uiState.update { it.copy(loading = false, errorMessage = e.message) }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        val deviceId = state.deviceId ?: return
        if (!state.hasMore || state.loadingMore) return

        _uiState.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            try {
                val proximaPagina = eventApi.listarEventos(deviceId, page = state.page + 1, size = PAGE_SIZE)
                _uiState.update {
                    it.copy(
                        loadingMore = false,
                        eventos = it.eventos + proximaPagina.content,
                        page = proximaPagina.number,
                        hasMore = !proximaPagina.last,
                    )
                }
            } catch (e: ApiError) {
                _uiState.update { it.copy(loadingMore = false, errorMessage = e.message) }
            }
        }
    }
}
