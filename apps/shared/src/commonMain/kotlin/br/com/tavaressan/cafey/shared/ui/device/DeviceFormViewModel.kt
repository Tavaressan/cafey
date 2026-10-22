package br.com.tavaressan.cafey.shared.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.tavaressan.cafey.shared.ble.BleClient
import br.com.tavaressan.cafey.shared.ble.BleDispositivo
import br.com.tavaressan.cafey.shared.ble.DispositivoInfo
import br.com.tavaressan.cafey.shared.ble.WifiProvisionState
import br.com.tavaressan.cafey.shared.domain.model.AtualizarDispositivoRequest
import br.com.tavaressan.cafey.shared.domain.model.CriarDispositivoRequest
import br.com.tavaressan.cafey.shared.domain.validation.DeviceFormErrors
import br.com.tavaressan.cafey.shared.network.ApiError
import br.com.tavaressan.cafey.shared.network.DeviceApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// Padrões de `Dispositivo` no backend, pré-preenchidos no cadastro.
private const val DEFAULT_TIMEZONE = "America/Sao_Paulo"
private const val DEFAULT_DURATION_S = 300
private const val DEFAULT_DESCALING_THRESHOLD = 200

// Teto do passo "Conectando ao Wi-Fi": a conexão BLE pode levar até ~20 s (2 tentativas de 10 s)
// e o firmware só desiste da rede após 20 s; sem resposta além disso, a notificação se perdeu.
private const val WIFI_PROVISION_TIMEOUT_MS = 45_000L

enum class DeviceFormStep {
    MANUAL,
    BLE_SEARCH,
    WIFI_CREDENTIALS,
    WIFI_CONNECTING,
    CONFIRM_REGISTER,
}

/** Campos do formulário e estados de fluxo assistido por BLE (UC-04 #249) e manual (APP-13). */
data class DeviceFormUiState(
    /** `null` = cadastro de um dispositivo novo; preenchido = edição. */
    val deviceId: String? = null,
    val loading: Boolean = false,
    val nome: String = "",
    val timezone: String = DEFAULT_TIMEZONE,
    val duracaoPreparoS: String = DEFAULT_DURATION_S.toString(),
    val limiarDescalcificacao: String = DEFAULT_DESCALING_THRESHOLD.toString(),
    val errors: DeviceFormErrors = DeviceFormErrors(),
    val saving: Boolean = false,
    val errorMessage: String? = null,
    val success: Boolean = false,

    // Fluxo assistido BLE (UC-04 #249)
    val bleSupported: Boolean = false,
    val step: DeviceFormStep = DeviceFormStep.MANUAL,
    val scanning: Boolean = false,
    val discoveredDevices: List<BleDispositivo> = emptyList(),
    val selectedDevice: BleDispositivo? = null,
    val deviceInfo: DispositivoInfo? = null,
    val wifiSsid: String = "",
    val wifiPassword: String = "",
    val wifiPasswordVisible: Boolean = false,
    val wifiConnecting: Boolean = false,
    val wifiStatusMessage: String? = null,
)

/** Formulário de dispositivo: cadastro assistido por BLE (UC-04 #249), manual (APP-13) e edição/exclusão. */
class DeviceFormViewModel(
    private val deviceApi: DeviceApi,
    private val bleClient: BleClient? = null,
    deviceId: String? = null,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope

    constructor(deviceApi: DeviceApi, deviceId: String?) : this(deviceApi, null, deviceId, null)
    constructor(deviceApi: DeviceApi, deviceId: String?, coroutineScope: CoroutineScope?) : this(deviceApi, null, deviceId, coroutineScope)

    private val isBleAvailable = deviceId == null && (bleClient != null && bleClient.isSupported)

    private val _uiState = MutableStateFlow(
        DeviceFormUiState(
            deviceId = deviceId,
            loading = deviceId != null,
            bleSupported = isBleAvailable,
            step = if (isBleAvailable) DeviceFormStep.BLE_SEARCH else DeviceFormStep.MANUAL,
        )
    )
    val uiState: StateFlow<DeviceFormUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private var provisionJob: Job? = null

    init {
        if (deviceId != null) {
            scope.launch { load(deviceId) }
        } else if (isBleAvailable) {
            startBleScan()
        }
    }

    private suspend fun load(deviceId: String) {
        try {
            val device = deviceApi.obter(deviceId)
            _uiState.update {
                it.copy(
                    loading = false,
                    nome = device.nome,
                    timezone = device.timezone,
                    duracaoPreparoS = device.duracaoPreparoS.toString(),
                    limiarDescalcificacao = device.limiarDescalcificacao.toString(),
                )
            }
        } catch (e: ApiError) {
            _uiState.update { it.copy(loading = false, errorMessage = e.message) }
        }
    }

    fun startBleScan() {
        val client = bleClient ?: return
        _uiState.update { it.copy(scanning = true, errorMessage = null) }
        scanJob?.cancel()
        scanJob = scope.launch {
            client.escanearDispositivos()
                .catch { e ->
                    _uiState.update { it.copy(scanning = false, errorMessage = e.message ?: "Falha ao escanear") }
                }
                .collect { list ->
                    _uiState.update { it.copy(discoveredDevices = list, scanning = false) }
                }
        }
    }

    fun selectDevice(device: BleDispositivo) {
        val client = bleClient ?: return
        scanJob?.cancel()
        scanJob = null
        _uiState.update { it.copy(loading = true, selectedDevice = device, errorMessage = null) }
        scope.launch {
            try {
                val info = client.lerInfoDispositivo(device.id)
                _uiState.update {
                    it.copy(
                        loading = false,
                        deviceInfo = info,
                        step = DeviceFormStep.WIFI_CREDENTIALS,
                    )
                }
            } catch (e: Throwable) {
                val detail = e.message ?: e::class.simpleName ?: "erro desconhecido"
                _uiState.update {
                    it.copy(
                        loading = false,
                        errorMessage = "Não foi possível conectar à cafeteira ($detail). Aproxime o celular e tente novamente.",
                    )
                }
            }
        }
    }

    fun onWifiSsidChange(value: String) {
        _uiState.update { it.copy(wifiSsid = value, errorMessage = null) }
    }

    fun onWifiPasswordChange(value: String) {
        _uiState.update { it.copy(wifiPassword = value, errorMessage = null) }
    }

    fun toggleWifiPasswordVisibility() {
        _uiState.update { it.copy(wifiPasswordVisible = !it.wifiPasswordVisible) }
    }

    fun provisionWifi() {
        val state = _uiState.value
        val client = bleClient ?: return
        val deviceId = state.selectedDevice?.id ?: return

        if (state.wifiSsid.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Nome da rede Wi-Fi (SSID) é obrigatório.") }
            return
        }

        _uiState.update {
            it.copy(
                step = DeviceFormStep.WIFI_CONNECTING,
                wifiConnecting = true,
                wifiStatusMessage = "Conectando à rede Wi-Fi...",
                errorMessage = null,
            )
        }

        provisionJob?.cancel()
        provisionJob = scope.launch {
            val finished = withTimeoutOrNull(WIFI_PROVISION_TIMEOUT_MS) {
                collectProvisioning(client, deviceId, state)
            }
            if (finished == null) {
                _uiState.update {
                    it.copy(
                        wifiConnecting = false,
                        step = DeviceFormStep.WIFI_CREDENTIALS,
                        errorMessage = "Sem resposta da cafeteira. Confira se ela está ligada e perto do celular e tente novamente.",
                    )
                }
            }
        }
    }

    /** Botão "Cancelar" do passo de conexão: encerra a escuta BLE e volta às credenciais. */
    fun cancelProvisioning() {
        provisionJob?.cancel()
        provisionJob = null
        _uiState.update {
            it.copy(
                wifiConnecting = false,
                step = DeviceFormStep.WIFI_CREDENTIALS,
                wifiStatusMessage = null,
                errorMessage = null,
            )
        }
    }

    private suspend fun collectProvisioning(client: BleClient, deviceId: String, state: DeviceFormUiState) {
        client.provisionarWifi(deviceId, state.wifiSsid, state.wifiPassword)
            .catch { e ->
                _uiState.update {
                    it.copy(
                        wifiConnecting = false,
                        step = DeviceFormStep.WIFI_CREDENTIALS,
                        errorMessage = e.message ?: "Falha ao enviar credenciais Wi-Fi",
                    )
                }
            }
            .collect { status ->
                when (status.state) {
                    WifiProvisionState.CONNECTING -> {
                        _uiState.update { it.copy(wifiStatusMessage = "Conectando à rede Wi-Fi...") }
                    }
                    WifiProvisionState.CONNECTED -> {
                        val defaultName = state.selectedDevice?.nome?.ifBlank {
                            state.deviceInfo?.serial ?: "Cafeteira"
                        } ?: "Cafeteira"
                        _uiState.update {
                            it.copy(
                                wifiConnecting = false,
                                step = DeviceFormStep.CONFIRM_REGISTER,
                                nome = if (it.nome.isBlank()) defaultName else it.nome,
                                wifiStatusMessage = "Wi-Fi conectado com sucesso!",
                            )
                        }
                    }
                    WifiProvisionState.AUTH_FAILED -> {
                        _uiState.update {
                            it.copy(
                                wifiConnecting = false,
                                step = DeviceFormStep.WIFI_CREDENTIALS,
                                errorMessage = "Senha da rede Wi-Fi incorreta. Verifique os dados e tente novamente.",
                            )
                        }
                    }
                    WifiProvisionState.TIMEOUT -> {
                        _uiState.update {
                            it.copy(
                                wifiConnecting = false,
                                step = DeviceFormStep.WIFI_CREDENTIALS,
                                errorMessage = "Não foi possível encontrar a rede Wi-Fi. Verifique o sinal.",
                            )
                        }
                    }
                    WifiProvisionState.INVALID_PAYLOAD -> {
                        _uiState.update {
                            it.copy(
                                wifiConnecting = false,
                                step = DeviceFormStep.WIFI_CREDENTIALS,
                                errorMessage = "Dados da rede Wi-Fi inválidos.",
                            )
                        }
                    }
                    WifiProvisionState.DISCONNECTED -> {
                        // Estado neutro/aguardando
                    }
                }
            }
    }

    fun setStep(step: DeviceFormStep) {
        if (step != DeviceFormStep.BLE_SEARCH) {
            scanJob?.cancel()
            scanJob = null
        }
        _uiState.update { it.copy(step = step, errorMessage = null) }
        if (step == DeviceFormStep.BLE_SEARCH) {
            startBleScan()
        }
    }

    fun switchToManual() = setStep(DeviceFormStep.MANUAL)

    fun switchToBle() {
        if (_uiState.value.bleSupported) {
            setStep(DeviceFormStep.BLE_SEARCH)
        }
    }

    fun onNomeChange(value: String) {
        _uiState.update { it.copy(nome = value, errors = it.errors.copy(nome = null), errorMessage = null) }
    }

    fun onTimezoneChange(value: String) {
        _uiState.update { it.copy(timezone = value, errorMessage = null) }
    }

    fun onDuracaoPreparoSChange(value: String) {
        _uiState.update { it.copy(duracaoPreparoS = value, errors = it.errors.copy(duracaoPreparoS = null), errorMessage = null) }
    }

    fun onLimiarDescalcificacaoChange(value: String) {
        _uiState.update {
            it.copy(limiarDescalcificacao = value, errors = it.errors.copy(limiarDescalcificacao = null), errorMessage = null)
        }
    }

    /** Botão "Limpar Campos": esvazia o formulário sem sair dele. */
    fun clearFields() {
        _uiState.update {
            it.copy(
                nome = "",
                timezone = "",
                duracaoPreparoS = "",
                limiarDescalcificacao = "",
                errors = DeviceFormErrors(),
                errorMessage = null,
            )
        }
    }

    /** Botão "Gravar": cadastra ou atualiza, conforme [DeviceFormUiState.deviceId]. */
    fun save() {
        val state = _uiState.value
        val errors = DeviceFormErrors.validate(state.nome, state.duracaoPreparoS, state.limiarDescalcificacao)
        _uiState.update { it.copy(errors = errors) }
        if (!errors.isValid) return

        val timezone = state.timezone.trim().ifEmpty { null }
        val duracaoPreparoS = state.duracaoPreparoS.trim().toIntOrNull()
        val limiarDescalcificacao = state.limiarDescalcificacao.trim().toIntOrNull()

        _uiState.update { it.copy(saving = true, errorMessage = null) }
        scope.launch {
            try {
                if (state.deviceId == null) {
                    // Só o cadastro por BLE manda o serial: é ele que liga o registro à thing da placa.
                    val serial = state.deviceInfo?.serial?.takeIf { state.step == DeviceFormStep.CONFIRM_REGISTER }
                    deviceApi.criar(
                        CriarDispositivoRequest(state.nome, timezone, duracaoPreparoS, limiarDescalcificacao, serial)
                    )
                } else {
                    deviceApi.atualizar(
                        state.deviceId,
                        AtualizarDispositivoRequest(state.nome, timezone, duracaoPreparoS, limiarDescalcificacao),
                    )
                }
                _uiState.update { it.copy(saving = false, success = true) }
            } catch (e: ApiError) {
                _uiState.update { it.copy(saving = false, errorMessage = e.message) }
            }
        }
    }

    fun delete() {
        val deviceId = _uiState.value.deviceId ?: return
        _uiState.update { it.copy(saving = true, errorMessage = null) }
        scope.launch {
            try {
                deviceApi.excluir(deviceId)
                _uiState.update { it.copy(saving = false, success = true) }
            } catch (e: ApiError) {
                _uiState.update { it.copy(saving = false, errorMessage = e.message) }
            }
        }
    }
}
