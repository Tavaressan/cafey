package br.com.tavaressan.cafey.shared.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.tavaressan.cafey.shared.ble.BleClient
import br.com.tavaressan.cafey.shared.ble.BleCommandFallback
import br.com.tavaressan.cafey.shared.ble.BleError
import br.com.tavaressan.cafey.shared.ble.ComandoResultado
import br.com.tavaressan.cafey.shared.domain.model.AcaoComando
import br.com.tavaressan.cafey.shared.domain.model.ComandoResponse
import br.com.tavaressan.cafey.shared.domain.model.DeviceState
import br.com.tavaressan.cafey.shared.domain.model.DispositivoResponse
import br.com.tavaressan.cafey.shared.domain.model.ProximoPreparo
import br.com.tavaressan.cafey.shared.domain.model.calcularProximoPreparo
import br.com.tavaressan.cafey.shared.network.ApiError
import br.com.tavaressan.cafey.shared.network.CommandApi
import br.com.tavaressan.cafey.shared.network.DeviceApi
import br.com.tavaressan.cafey.shared.network.EventApi
import br.com.tavaressan.cafey.shared.network.ScheduleApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Janela de tolerância para considerar o dispositivo desconectado (spec §6.3: > 2 minutos sem atualização). */
internal const val OFFLINE_TOLERANCE_MS = 120_000L

/** Intervalo de sondagem do estado do dispositivo (UC-08). O backend (`EventoController` /
 * `DispositivoController`) só expõe REST, sem stream nem WebSocket — sem push disponível, 5s é
 * um meio-termo razoável entre "parecer tempo real" e não afogar a bateria/rede do celular com
 * polling. O requisito de desempenho da spec (§9, <2s) é sobre o comando chegar à base via MQTT,
 * não sobre o app perceber o resultado — a sondagem só reflete esse estado, não o causa. */
private const val POLL_INTERVAL_MS = 5_000L
/** Intervalo de atualização da sequência de manhãs (métrica analítica diária; não precisa rodar a cada 5s). */
private const val STATS_POLL_INTERVAL_MS = 60_000L

/**
 * Avalia se o dispositivo deve ser considerado offline:
 * - Se `device.online == false` (notificado via Last Will pelo broker ou backend)
 * - Se `ultimoVisto` exceder a janela de tolerância de 2 minutos em relação ao tempo atual.
 */
internal fun isDispositivoOffline(device: DispositivoResponse?, nowMs: Long): Boolean {
    if (device == null) return false
    if (!device.online) return true
    val ultimoVisto = device.ultimoVisto ?: return false
    return try {
        val ultimoVistoMs = Instant.parse(ultimoVisto).toEpochMilliseconds()
        (nowMs - ultimoVistoMs) > OFFLINE_TOLERANCE_MS
    } catch (_: Exception) {
        false
    }
}

data class HomeUiState(
    val loading: Boolean = true,
    val device: DispositivoResponse? = null,
    val devices: List<DispositivoResponse> = emptyList(),
    val commandInFlight: Boolean = false,
    val errorMessage: String? = null,
    val proximoPreparo: ProximoPreparo? = null,
    val sequenciaManhasDias: Int = 0,
    val isOffline: Boolean = false,
) {
    val isDeviceOffline: Boolean get() = isOffline
    val deviceState: DeviceState get() = device?.let { DeviceState.from(it.estado) } ?: DeviceState.Unknown("")
    val canSendCommand: Boolean get() = device != null && !loading && !commandInFlight && !isOffline
}

class HomeViewModel(
    private val deviceApi: DeviceApi,
    private val commandApi: CommandApi,
    private val scheduleApi: ScheduleApi,
    private val eventApi: EventApi,
    private val bleClient: BleClient? = null,
    private val activeDeviceId: MutableStateFlow<String?>? = null,
    coroutineScope: kotlinx.coroutines.CoroutineScope? = null,
    private val nowProvider: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {
    constructor(
        deviceApi: DeviceApi,
        commandApi: CommandApi,
        scheduleApi: ScheduleApi,
        eventApi: EventApi,
        bleClient: BleClient?,
        coroutineScope: kotlinx.coroutines.CoroutineScope?,
    ) : this(deviceApi, commandApi, scheduleApi, eventApi, bleClient, null, coroutineScope)

    private val scope = coroutineScope ?: viewModelScope
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private var lastStatsFetchMs = 0L
    private var selectedDeviceId: String? = null

    private val fallback: BleCommandFallback? =
        if (bleClient != null && bleClient.isSupported) {
            BleCommandFallback(commandApi, bleClient)
        } else {
            null
        }

    init {
        if (activeDeviceId != null) {
            scope.launch {
                activeDeviceId.collect { newId ->
                    if (newId != null && _uiState.value.device?.id != newId) {
                        selectedDeviceId = newId
                        lastStatsFetchMs = 0L
                        refresh()
                    }
                }
            }
        }
        scope.launch {
            while (isActive) {
                refresh()
                kotlinx.coroutines.delay(POLL_INTERVAL_MS)
            }
        }
    }

    internal suspend fun refresh() {
        try {
            val devices = deviceApi.listar()
            val targetId = activeDeviceId?.value ?: selectedDeviceId
            val device = devices.firstOrNull { it.id == targetId } ?: devices.firstOrNull()
            if (device != null) {
                selectedDeviceId = device.id
                if (activeDeviceId != null && activeDeviceId.value != device.id) {
                    activeDeviceId.value = device.id
                }
            }
            val proximoPreparo = device?.let { buscarProximoPreparo(it.id) }
            val nowMs = nowProvider()
            val isOffline = isDispositivoOffline(device, nowMs)
            val sequenciaManhasDias = if (device != null) {
                if (nowMs - lastStatsFetchMs >= STATS_POLL_INTERVAL_MS || _uiState.value.sequenciaManhasDias == 0) {
                    lastStatsFetchMs = nowMs
                    buscarSequenciaManhas(device.id)
                } else {
                    _uiState.value.sequenciaManhasDias
                }
            } else {
                0
            }
            _uiState.update { current ->
                val errorParaManter = if (current.device == null) null else current.errorMessage
                current.copy(
                    loading = false,
                    device = device,
                    devices = devices,
                    errorMessage = errorParaManter,
                    proximoPreparo = proximoPreparo,
                    sequenciaManhasDias = sequenciaManhasDias,
                    isOffline = isOffline,
                )
            }
        } catch (e: ApiError) {
            _uiState.update { it.copy(loading = false, errorMessage = e.message) }
        }
    }

    fun selectDevice(id: String) {
        if (selectedDeviceId == id && _uiState.value.device?.id == id) return
        selectedDeviceId = id
        activeDeviceId?.value = id
        lastStatsFetchMs = 0L
        scope.launch {
            _uiState.update { it.copy(loading = true) }
            refresh()
        }
    }

    // Issue #176 — hora local do próprio aparelho, não o timezone salvo no dispositivo: é isso
    // que o usuário vê no relógio dele, e a spec de agendamento (APP-05) não define qual dos dois
    // deveria prevalecer na Home. Se um agendamento vier malformado (hora fora do padrão HH:mm),
    // ignora a lista em vez de quebrar a tela — a falha já é visível no cartão "Agendamentos".
    @OptIn(ExperimentalTime::class)
    private suspend fun buscarProximoPreparo(dispositivoId: String): ProximoPreparo? = try {
        val agendamentos = scheduleApi.listar(dispositivoId)
        val agora = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        calcularProximoPreparo(agendamentos, agora)
    } catch (e: ApiError) {
        null
    }

    // Issue #177 — backend calcula a sequência real (EventoService.obterEstatisticas ->
    // SequenciaManhas); aqui só consumimos o campo. 0 (o default de HomeUiState) já é o estado
    // vazio correto, então uma falha de rede aqui cai discretamente nele em vez de quebrar a tela.
    private suspend fun buscarSequenciaManhas(dispositivoId: String): Int = try {
        eventApi.obterEstatisticas(dispositivoId).sequenciaManhasDias
    } catch (e: ApiError) {
        0
    }

    fun ligar() = runCommand(AcaoComando.LIGAR)

    fun cancelar() = runCommand(AcaoComando.CANCELAR)

    // O backend responde 202 sem o estado novo: ele só publicou o comando no MQTT. Por isso
    // relemos o dispositivo em vez de aproveitar a resposta — o estado muda quando a base reporta.
    // Em caso de falha de rede da nuvem, BleCommandFallback executa localmente via BLE (UC-06/07).
    private fun runCommand(acao: AcaoComando) {
        if (!_uiState.value.canSendCommand) return
        val deviceId = _uiState.value.device?.id ?: return
        _uiState.update { it.copy(commandInFlight = true, errorMessage = null) }
        scope.launch {
            try {
                val resultado = if (fallback != null) {
                    fallback.executar(deviceId, acao)
                } else {
                    when (acao) {
                        AcaoComando.LIGAR -> commandApi.ligar(deviceId)
                        AcaoComando.DESLIGAR -> commandApi.desligar(deviceId)
                        AcaoComando.CANCELAR -> commandApi.cancelar(deviceId)
                    }
                    ComandoResultado.ViaNuvem(ComandoResponse("", acao, 0, ""))
                }

                if (resultado is ComandoResultado.ViaBle) {
                    val estadoEstimado = when (acao) {
                        AcaoComando.LIGAR -> "PREPARANDO"
                        AcaoComando.DESLIGAR, AcaoComando.CANCELAR -> "PRONTO"
                    }
                    _uiState.update {
                        it.copy(
                            commandInFlight = false,
                            device = it.device?.copy(estado = estadoEstimado),
                            errorMessage = null,
                        )
                    }
                } else {
                    lastStatsFetchMs = 0L
                    refresh()
                    _uiState.update { it.copy(commandInFlight = false) }
                }
            } catch (e: BleError) {
                _uiState.update { it.copy(commandInFlight = false, errorMessage = e.message) }
            } catch (e: ApiError) {
                _uiState.update { it.copy(commandInFlight = false, errorMessage = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(commandInFlight = false, errorMessage = e.message ?: "Falha ao executar comando") }
            }
        }
    }
}
