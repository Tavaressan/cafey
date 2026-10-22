package br.com.tavaressan.cafey.shared.ui.device

import br.com.tavaressan.cafey.shared.AppContainer
import br.com.tavaressan.cafey.shared.auth.FakeTokenStorage
import br.com.tavaressan.cafey.shared.auth.StoredTokens
import br.com.tavaressan.cafey.shared.ble.BleDispositivo
import br.com.tavaressan.cafey.shared.ble.BleError
import br.com.tavaressan.cafey.shared.ble.DispositivoInfo
import br.com.tavaressan.cafey.shared.ble.FakeBleClient
import br.com.tavaressan.cafey.shared.ble.WifiProvisionState
import br.com.tavaressan.cafey.shared.ble.WifiProvisionStatus
import br.com.tavaressan.cafey.shared.network.ApiClient
import br.com.tavaressan.cafey.shared.network.DeviceApi
import br.com.tavaressan.cafey.shared.network.sharedHttpClientConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceFormViewModelBleTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var appContainer: AppContainer

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val tokenStorage = FakeTokenStorage(StoredTokens("demo-access", "demo-refresh"))
        appContainer = AppContainer.demo(tokenStorage = tokenStorage)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStep_withoutBle_isManual() = runTest(testDispatcher) {
        val viewModel = DeviceFormViewModel(
            deviceApi = appContainer.deviceApi,
            bleClient = null,
            coroutineScope = this,
        )

        val state = viewModel.uiState.value
        assertEquals(DeviceFormStep.MANUAL, state.step)
        assertFalse(state.bleSupported)
    }

    @Test
    fun initialStep_withBle_startsBleSearchAndDiscoversDevices() = runTest(testDispatcher) {
        val fakeBle = FakeBleClient(
            dispositivosEscaneados = listOf(
                BleDispositivo(id = "cafe-01", nome = "Cafey Cozinha", rssi = -60)
            )
        )
        val viewModel = DeviceFormViewModel(
            deviceApi = appContainer.deviceApi,
            bleClient = fakeBle,
            coroutineScope = this,
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(DeviceFormStep.BLE_SEARCH, state.step)
        assertTrue(state.bleSupported)
        assertEquals(1, state.discoveredDevices.size)
        assertEquals("cafe-01", state.discoveredDevices.first().id)
    }

    @Test
    fun switchBetweenBleAndManual() = runTest(testDispatcher) {
        val fakeBle = FakeBleClient()
        val viewModel = DeviceFormViewModel(
            deviceApi = appContainer.deviceApi,
            bleClient = fakeBle,
            coroutineScope = this,
        )

        advanceUntilIdle()
        assertEquals(DeviceFormStep.BLE_SEARCH, viewModel.uiState.value.step)

        viewModel.switchToManual()
        assertEquals(DeviceFormStep.MANUAL, viewModel.uiState.value.step)

        viewModel.switchToBle()
        assertEquals(DeviceFormStep.BLE_SEARCH, viewModel.uiState.value.step)
    }

    @Test
    fun selectDevice_readsDeviceInfoAndAdvancesToCredentials() = runTest(testDispatcher) {
        val fakeBle = FakeBleClient(
            infoDispositivoRetorno = DispositivoInfo(serial = "cafey-proto-999")
        )
        val viewModel = DeviceFormViewModel(
            deviceApi = appContainer.deviceApi,
            bleClient = fakeBle,
            coroutineScope = this,
        )
        advanceUntilIdle()

        val device = BleDispositivo("dev-1", "Minha Cafeteira")
        viewModel.selectDevice(device)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(DeviceFormStep.WIFI_CREDENTIALS, state.step)
        assertEquals("cafey-proto-999", state.deviceInfo?.serial)
        assertEquals(device, state.selectedDevice)
    }

    @Test
    fun selectDevice_failureShowsErrorMessage() = runTest(testDispatcher) {
        val fakeBle = FakeBleClient(erroAoLerInfo = BleError.BaseNaoEncontrada)
        val viewModel = DeviceFormViewModel(
            deviceApi = appContainer.deviceApi,
            bleClient = fakeBle,
            coroutineScope = this,
        )
        advanceUntilIdle()

        viewModel.selectDevice(BleDispositivo("dev-1", "Minha Cafeteira"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(DeviceFormStep.BLE_SEARCH, state.step)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage.contains("Não foi possível conectar"))
    }

    @Test
    fun provisionWifi_success_advancesToConfirmRegister_andSaves() = runTest(testDispatcher) {
        val fakeBle = FakeBleClient(
            statusProvisionamentoRetorno = listOf(
                WifiProvisionStatus(WifiProvisionState.CONNECTING),
                WifiProvisionStatus(WifiProvisionState.CONNECTED, ip = "192.168.1.100"),
            )
        )
        val viewModel = DeviceFormViewModel(
            deviceApi = appContainer.deviceApi,
            bleClient = fakeBle,
            coroutineScope = this,
        )
        advanceUntilIdle()

        viewModel.selectDevice(BleDispositivo("dev-1", "Cafey Expresso"))
        advanceUntilIdle()

        viewModel.onWifiSsidChange("RedeCasa")
        viewModel.onWifiPasswordChange("Senha123")
        viewModel.provisionWifi()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(DeviceFormStep.CONFIRM_REGISTER, state.step)
        assertEquals("Cafey Expresso", state.nome)
        assertTrue(state.wifiStatusMessage!!.contains("sucesso"))

        viewModel.save()
        val finalState = viewModel.uiState.first { it.success || it.errorMessage != null }
        assertTrue(finalState.success)
    }

    @Test
    fun provisionWifi_authFailed_returnsToCredentials_withErrorMessage() = runTest(testDispatcher) {
        val fakeBle = FakeBleClient(
            statusProvisionamentoRetorno = listOf(
                WifiProvisionStatus(WifiProvisionState.AUTH_FAILED, error = "Senha incorreta")
            )
        )
        val viewModel = DeviceFormViewModel(
            deviceApi = appContainer.deviceApi,
            bleClient = fakeBle,
            coroutineScope = this,
        )
        advanceUntilIdle()

        viewModel.selectDevice(BleDispositivo("dev-1", "Cafey Expresso"))
        advanceUntilIdle()

        viewModel.onWifiSsidChange("RedeCasa")
        viewModel.onWifiPasswordChange("SenhaErrada")
        viewModel.provisionWifi()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(DeviceFormStep.WIFI_CREDENTIALS, state.step)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage.contains("Senha da rede Wi-Fi incorreta"))
    }

    @Test
    fun provisionWifi_timeout_returnsToCredentials_withErrorMessage() = runTest(testDispatcher) {
        val fakeBle = FakeBleClient(
            statusProvisionamentoRetorno = listOf(
                WifiProvisionStatus(WifiProvisionState.TIMEOUT)
            )
        )
        val viewModel = DeviceFormViewModel(
            deviceApi = appContainer.deviceApi,
            bleClient = fakeBle,
            coroutineScope = this,
        )
        advanceUntilIdle()

        viewModel.selectDevice(BleDispositivo("dev-1", "Cafey Expresso"))
        advanceUntilIdle()

        viewModel.onWifiSsidChange("RedeLonge")
        viewModel.onWifiPasswordChange("123456")
        viewModel.provisionWifi()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(DeviceFormStep.WIFI_CREDENTIALS, state.step)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage.contains("sinal"))
    }

    @Test
    fun provisionWifi_noFinalStatus_timesOutAndReturnsToCredentials() = runTest(testDispatcher) {
        val fakeBle = FakeBleClient(
            statusProvisionamentoRetorno = listOf(WifiProvisionStatus(WifiProvisionState.CONNECTING)),
            manterProvisionamentoAberto = true,
        )
        val viewModel = DeviceFormViewModel(
            deviceApi = appContainer.deviceApi,
            bleClient = fakeBle,
            coroutineScope = this,
        )
        advanceUntilIdle()

        viewModel.selectDevice(BleDispositivo("dev-1", "Cafey Expresso"))
        advanceUntilIdle()

        viewModel.onWifiSsidChange("RedeCasa")
        viewModel.provisionWifi()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(DeviceFormStep.WIFI_CREDENTIALS, state.step)
        assertFalse(state.wifiConnecting)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage.contains("Sem resposta da cafeteira"))
    }

    @Test
    fun cancelProvisioning_returnsToCredentials_withoutLateTimeoutError() = runTest(testDispatcher) {
        val fakeBle = FakeBleClient(
            statusProvisionamentoRetorno = listOf(WifiProvisionStatus(WifiProvisionState.CONNECTING)),
            manterProvisionamentoAberto = true,
        )
        val viewModel = DeviceFormViewModel(
            deviceApi = appContainer.deviceApi,
            bleClient = fakeBle,
            coroutineScope = this,
        )
        advanceUntilIdle()

        viewModel.selectDevice(BleDispositivo("dev-1", "Cafey Expresso"))
        advanceUntilIdle()

        viewModel.onWifiSsidChange("RedeCasa")
        viewModel.provisionWifi()
        runCurrent()
        assertEquals(DeviceFormStep.WIFI_CONNECTING, viewModel.uiState.value.step)

        viewModel.cancelProvisioning()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(DeviceFormStep.WIFI_CREDENTIALS, state.step)
        assertFalse(state.wifiConnecting)
        assertNull(state.errorMessage)
    }

    @Test
    fun save_afterBleProvisioning_sendsDeviceSerial() = runTest(testDispatcher) {
        val serial = "c40a1340-e5cf-4290-8fd4-f6e838d80fb5"
        var corpoPost: String? = null
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Post && request.url.encodedPath == "/dispositivos") {
                corpoPost = request.body.toByteArray().decodeToString()
            }
            respond(
                content = """{"id":"$serial","nome":"Cafey Expresso","timezone":"America/Sao_Paulo","estado":"DESLIGADO","online":true,"versaoAgendamentos":3,"duracaoPreparoS":300,"limiarDescalcificacao":200,"contadorPreparos":0,"papel":"PROPRIETARIO","criadoEm":"2026-10-21T10:00:00Z","atualizadoEm":"2026-10-21T10:00:00Z"}""",
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val tokenStorage = FakeTokenStorage(StoredTokens("demo-access", "demo-refresh"))
        val httpClient = HttpClient(engine, sharedHttpClientConfig("https://api.cafey.test", tokenStorage))
        val viewModel = DeviceFormViewModel(
            deviceApi = DeviceApi(ApiClient("https://api.cafey.test", tokenStorage, httpClient)),
            bleClient = FakeBleClient(infoDispositivoRetorno = DispositivoInfo(serial = serial)),
            coroutineScope = this,
        )
        advanceUntilIdle()

        viewModel.selectDevice(BleDispositivo("dev-1", "Cafey Expresso"))
        advanceUntilIdle()
        viewModel.onWifiSsidChange("RedeCasa")
        viewModel.provisionWifi()
        advanceUntilIdle()
        assertEquals(DeviceFormStep.CONFIRM_REGISTER, viewModel.uiState.value.step)

        viewModel.save()
        val finalState = viewModel.uiState.first { it.success || it.errorMessage != null }

        assertTrue(finalState.success)
        assertNotNull(corpoPost)
        assertTrue(corpoPost!!.contains(""""serial":"$serial""""))
    }

    @Test
    fun editExistingDevice_alwaysManual() = runTest(testDispatcher) {
        val fakeBle = FakeBleClient()
        val viewModel = DeviceFormViewModel(
            deviceApi = appContainer.deviceApi,
            bleClient = fakeBle,
            deviceId = "cafey-demo-cp30",
            coroutineScope = this,
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(DeviceFormStep.MANUAL, state.step)
        assertFalse(state.bleSupported)
    }
}
