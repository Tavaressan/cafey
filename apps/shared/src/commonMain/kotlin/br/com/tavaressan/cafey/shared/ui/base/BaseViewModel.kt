package br.com.tavaressan.cafey.shared.ui.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.tavaressan.cafey.shared.ble.BleClient
import br.com.tavaressan.cafey.shared.ble.BleError
import br.com.tavaressan.cafey.shared.ble.WifiNetworksInfo
import br.com.tavaressan.cafey.shared.domain.model.DiagnosticoDispositivoResponse
import br.com.tavaressan.cafey.shared.domain.model.DispositivoResponse
import br.com.tavaressan.cafey.shared.domain.model.PapelDispositivo
import br.com.tavaressan.cafey.shared.network.ApiError
import br.com.tavaressan.cafey.shared.network.CommandApi
import br.com.tavaressan.cafey.shared.network.DeviceApi
import br.com.tavaressan.cafey.shared.preferences.ThemePreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val CONFIRMAR_ESQUECER_TODAS = "__ALL__"

data class BaseUiState(
    val loading: Boolean = true,
    val device: DispositivoResponse? = null,
    val diagnostico: DiagnosticoDispositivoResponse? = null,
    val diagnosticoRestrito: Boolean = false,
    val darkTheme: Boolean = false,
    val errorMessage: String? = null,
    // FW-30 (#332): gerenciamento de redes Wi-Fi
    val wifiRedes: WifiNetworksInfo? = null,
    val wifiCarregando: Boolean = false,
    val wifiOperacaoEmAndamento: Boolean = false,
    val wifiErro: String? = null,
    val wifiBleDispositivoId: String? = null,
    /** SSID a confirmar exclusão, ou [CONFIRMAR_ESQUECER_TODAS]. null = sem diálogo. */
    val confirmarEsquecimento: String? = null,
)

/**
 * Issue #182 — tela "Base" (dispositivo + alternador de tema). Issue #321 adiciona o painel de
 * diagnóstico (restrito ao proprietário): dados persistidos pelo backend a partir do MQTT.
 * FW-30 (#332): gerenciamento de redes Wi-Fi (listar via BLE, esquecer com confirmação,
 * nuvem quando online).
 */
class BaseViewModel(
    private val deviceApi: DeviceApi,
    private val themePreference: ThemePreference,
    private val activeDeviceId: StateFlow<String?>? = null,
    coroutineScope: CoroutineScope? = null,
    private val commandApi: CommandApi? = null,
    private val bleClient: BleClient? = null,
) : ViewModel() {
    constructor(deviceApi: DeviceApi, themePreference: ThemePreference) :
        this(deviceApi, themePreference, null, null, null, null)

    private val scope = coroutineScope ?: viewModelScope
    private val _uiState = MutableStateFlow(BaseUiState())
    val uiState: StateFlow<BaseUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            themePreference.darkTheme.collect { dark -> _uiState.update { it.copy(darkTheme = dark) } }
        }
        if (activeDeviceId != null) {
            scope.launch {
                activeDeviceId.collect { id ->
                    if (id != null && _uiState.value.device?.id != id) {
                        loadDevice()
                    }
                }
            }
        }
    }

    /**
     * Chamado pela tela a cada vez que ela entra em composição, e não no `init`: ao voltar da edição
     * ou exclusão do dispositivo, o ViewModel é o mesmo e precisa buscar o dado de novo.
     */
    fun loadDevice() {
        scope.launch {
            try {
                val allDevices = deviceApi.listar()
                val targetId = activeDeviceId?.value
                val device = allDevices.firstOrNull { it.id == targetId } ?: allDevices.firstOrNull()
                var diagnostico: DiagnosticoDispositivoResponse? = null
                var diagnosticoRestrito = false
                if (device != null && device.papel == PapelDispositivo.PROPRIETARIO) {
                    try {
                        diagnostico = deviceApi.diagnostico(device.id)
                    } catch (_: ApiError) {
                        diagnosticoRestrito = true
                    }
                } else if (device != null) {
                    diagnosticoRestrito = true
                }
                _uiState.update {
                    it.copy(
                        loading = false,
                        device = device,
                        diagnostico = diagnostico,
                        diagnosticoRestrito = diagnosticoRestrito,
                        errorMessage = null
                    )
                }
            } catch (e: ApiError) {
                _uiState.update { it.copy(loading = false, errorMessage = e.message) }
            } catch (e: Throwable) {
                _uiState.update { it.copy(loading = false, errorMessage = e.message ?: "Falha ao carregar dispositivo.") }
            }
        }
    }

    fun toggleTheme() {
        scope.launch { themePreference.toggle() }
    }

    /** Tenta localizar a cafeteira por BLE e carregar redes salvas (FW-30). */
    fun tentarCarregarRedesWifi() {
        val client = bleClient ?: run {
            _uiState.update { it.copy(wifiErro = "Bluetooth indisponível neste dispositivo.") }
            return
        }
        scope.launch {
            _uiState.update { it.copy(wifiCarregando = true, wifiErro = null) }
            try {
                val encontrados = client.escanearDispositivos().first()
                val alvo = encontrados.firstOrNull()
                if (alvo == null) {
                    _uiState.update {
                        it.copy(wifiCarregando = false,
                            wifiErro = "Nenhuma cafeteira encontrada por Bluetooth. Aproxime-se dela.")
                    }
                } else {
                    val redes = client.listarRedesWifi(alvo.id)
                    _uiState.update { it.copy(wifiCarregando = false, wifiRedes = redes, wifiErro = null, wifiBleDispositivoId = alvo.id) }
                }
            } catch (e: BleError) {
                _uiState.update { it.copy(wifiCarregando = false, wifiErro = mensagemBle(e)) }
            } catch (e: Throwable) {
                _uiState.update { it.copy(wifiCarregando = false, wifiErro = e.message ?: "Falha ao carregar redes Wi-Fi.") }
            }
        }
    }

    /** Carrega redes salvas/atuais via BLE (FW-30). Requer proximidade da cafeteira. */
    fun carregarRedesWifi(bleDispositivoId: String) {
        val client = bleClient ?: run {
            _uiState.update { it.copy(wifiErro = "Bluetooth indisponível neste dispositivo.") }
            return
        }
        scope.launch {
            _uiState.update { it.copy(wifiCarregando = true, wifiErro = null) }
            try {
                val redes = client.listarRedesWifi(bleDispositivoId)
                _uiState.update { it.copy(wifiCarregando = false, wifiRedes = redes, wifiErro = null, wifiBleDispositivoId = bleDispositivoId) }
            } catch (e: BleError) {
                _uiState.update { it.copy(wifiCarregando = false, wifiErro = mensagemBle(e)) }
            } catch (e: Throwable) {
                _uiState.update { it.copy(wifiCarregando = false, wifiErro = e.message ?: "Falha ao carregar redes Wi-Fi.") }
            }
        }
    }

    fun solicitarEsquecerRede(ssid: String) {
        _uiState.update { it.copy(confirmarEsquecimento = ssid, wifiErro = null) }
    }

    fun solicitarEsquecerTodas() {
        _uiState.update { it.copy(confirmarEsquecimento = CONFIRMAR_ESQUECER_TODAS, wifiErro = null) }
    }

    fun cancelarConfirmacao() {
        _uiState.update { it.copy(confirmarEsquecimento = null) }
    }

    /**
     * Executa a exclusão confirmada. Usa BLE quando [bleDispositivoId] (ou o último BLE
     * conectado) está disponível; caso contrário tenta a nuvem (dispositivo online).
     * Trata erro/offline.
     */
    fun confirmarEsquecimento(bleDispositivoId: String? = null) {
        val alvo = _uiState.value.confirmarEsquecimento ?: return
        val bleId = bleDispositivoId ?: _uiState.value.wifiBleDispositivoId
        _uiState.update { it.copy(confirmarEsquecimento = null, wifiOperacaoEmAndamento = true, wifiErro = null) }
        scope.launch {
            try {
                val device = _uiState.value.device
                if (bleId != null && bleClient != null) {
                    if (alvo == CONFIRMAR_ESQUECER_TODAS) {
                        bleClient.esquecerTodasRedesWifi(bleId)
                        _uiState.update { it.copy(wifiRedes = WifiNetworksInfo()) }
                    } else {
                        bleClient.esquecerRedeWifi(bleId, alvo)
                        aplicarRemocaoLocal(alvo)
                    }
                } else if (commandApi != null && device != null && device.online) {
                    if (alvo == CONFIRMAR_ESQUECER_TODAS) {
                        commandApi.esquecerTodasRedesWifi(device.id)
                        _uiState.update { it.copy(wifiRedes = WifiNetworksInfo()) }
                    } else {
                        commandApi.esquecerRedeWifi(device.id, alvo)
                        aplicarRemocaoLocal(alvo)
                    }
                } else if (device == null || !device.online) {
                    _uiState.update {
                        it.copy(wifiErro = "Cafeteira offline. Aproxime-se dela e use o Bluetooth para gerenciar as redes.")
                    }
                    _uiState.update { it.copy(wifiOperacaoEmAndamento = false) }
                    return@launch
                } else {
                    _uiState.update { it.copy(wifiErro = "Não foi possível enviar o comando de Wi-Fi.") }
                    _uiState.update { it.copy(wifiOperacaoEmAndamento = false) }
                    return@launch
                }
                _uiState.update { it.copy(wifiOperacaoEmAndamento = false, wifiErro = null) }
            } catch (e: ApiError) {
                _uiState.update { it.copy(wifiOperacaoEmAndamento = false, wifiErro = e.message) }
            } catch (e: BleError) {
                _uiState.update { it.copy(wifiOperacaoEmAndamento = false, wifiErro = mensagemBle(e)) }
            } catch (e: Throwable) {
                _uiState.update { it.copy(wifiOperacaoEmAndamento = false, wifiErro = e.message ?: "Falha ao alterar redes Wi-Fi.") }
            }
        }
    }

    private fun aplicarRemocaoLocal(ssid: String) {
        val atual = _uiState.value.wifiRedes ?: return
        val restantes = atual.redes.filter { it != ssid }
        val ssidAtual = if (atual.ssid_atual == ssid) restantes.firstOrNull() else atual.ssid_atual
        _uiState.update { it.copy(wifiRedes = WifiNetworksInfo(ssid_atual = ssidAtual, redes = restantes)) }
    }

    private fun mensagemBle(e: BleError): String = when (e) {
        is BleError.BaseNaoEncontrada -> e.message ?: "Cafeteira não encontrada por Bluetooth."
        is BleError.ConexaoFalhou -> "Não foi possível conectar por Bluetooth à cafeteira."
        is BleError.Timeout -> "Tempo esgotado ao falar com a cafeteira por Bluetooth."
        is BleError.ProvisionamentoFalhou -> e.motivo
    }
}
