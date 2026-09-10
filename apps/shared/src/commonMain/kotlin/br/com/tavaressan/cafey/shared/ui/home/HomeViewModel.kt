package br.com.tavaressan.cafey.shared.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.tavaressan.cafey.shared.domain.model.ComandoResponse
import br.com.tavaressan.cafey.shared.domain.model.DeviceState
import br.com.tavaressan.cafey.shared.domain.model.DispositivoResponse
import br.com.tavaressan.cafey.shared.network.ApiError
import br.com.tavaressan.cafey.shared.network.CommandApi
import br.com.tavaressan.cafey.shared.network.DeviceApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Intervalo de sondagem do estado do dispositivo (UC-08). O backend (`EventoController` /
 * `DispositivoController`) só expõe REST, sem stream nem WebSocket — sem push disponível, 5s é
 * um meio-termo razoável entre "parecer tempo real" e não afogar a bateria/rede do celular com
 * polling. O requisito de desempenho da spec (§9, <2s) é sobre o comando chegar à base via MQTT,
 * não sobre o app perceber o resultado — a sondagem só reflete esse estado, não o causa. */
private const val POLL_INTERVAL_MS = 5_000L

data class HomeUiState(
    val loading: Boolean = true,
    val device: DispositivoResponse? = null,
    val commandInFlight: Boolean = false,
    val errorMessage: String? = null,
) {
    val deviceState: DeviceState get() = device?.let { DeviceState.from(it.estado) } ?: DeviceState.Unknown("")
}

class HomeViewModel(
    private val deviceApi: DeviceApi,
    private val commandApi: CommandApi,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                refresh()
                kotlinx.coroutines.delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun refresh() {
        try {
            // MVP de um único dispositivo — troca de aparelho fica para uma issue futura de
            // gerenciamento de dispositivos (fora do escopo de APP-04).
            val device = deviceApi.listar().firstOrNull()
            _uiState.update { it.copy(loading = false, device = device, errorMessage = null) }
        } catch (e: ApiError) {
            _uiState.update { it.copy(loading = false, errorMessage = e.message) }
        }
    }

    fun ligar() = runCommand { commandApi.ligar(it) }

    fun desligar() = runCommand { commandApi.desligar(it) }

    fun cancelar() = runCommand { commandApi.cancelar(it) }

    // O backend responde 202 sem o estado novo: ele só publicou o comando no MQTT. Por isso
    // relemos o dispositivo em vez de aproveitar a resposta — o estado muda quando a base reporta.
    private fun runCommand(action: suspend (deviceId: String) -> ComandoResponse) {
        val deviceId = _uiState.value.device?.id ?: return
        _uiState.update { it.copy(commandInFlight = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                action(deviceId)
                refresh()
                _uiState.update { it.copy(commandInFlight = false) }
            } catch (e: ApiError) {
                _uiState.update { it.copy(commandInFlight = false, errorMessage = e.message) }
            }
        }
    }
}
