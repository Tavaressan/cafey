package br.com.tavaressan.cafey.shared.ui.base

import br.com.tavaressan.cafey.shared.auth.FakeTokenStorage
import br.com.tavaressan.cafey.shared.auth.StoredTokens
import br.com.tavaressan.cafey.shared.ble.BleError
import br.com.tavaressan.cafey.shared.ble.FakeBleClient
import br.com.tavaressan.cafey.shared.ble.WifiNetworksInfo
import br.com.tavaressan.cafey.shared.network.ApiClient
import br.com.tavaressan.cafey.shared.network.CommandApi
import br.com.tavaressan.cafey.shared.network.DeviceApi
import br.com.tavaressan.cafey.shared.network.sharedHttpClientConfig
import br.com.tavaressan.cafey.shared.preferences.ThemePreference
import br.com.tavaressan.cafey.shared.preferences.ThemePreferenceStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BaseViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val tokenStorage = FakeTokenStorage(StoredTokens("access", "refresh"))

    private class MemoryThemeStorage(var value: Boolean? = false) : ThemePreferenceStorage {
        override suspend fun load(): Boolean? = value
        override suspend fun save(dark: Boolean) { value = dark }
    }

    @BeforeTest
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @AfterTest
    fun tearDown() { Dispatchers.resetMain() }

    private fun deviceJson(online: Boolean = true, papel: String = "PROPRIETARIO") = """
        [{
            "id":"dev-1","nome":"Cafeteira","timezone":"America/Sao_Paulo","estado":"PRONTO",
            "online":$online,"versaoAgendamentos":1,"duracaoPreparoS":300,
            "limiarDescalcificacao":100,"contadorPreparos":0,"papel":"$papel",
            "criadoEm":"2026-09-01T10:00:00Z","atualizadoEm":"2026-10-15T21:00:00Z"
        }]
    """.trimIndent()

    private fun apis(
        deviceBody: String = deviceJson(),
        comandoStatus: HttpStatusCode = HttpStatusCode.Accepted,
    ): Pair<DeviceApi, CommandApi> {
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath == "/dispositivos" ->
                    respond(deviceBody, HttpStatusCode.OK, jsonHeaders)
                request.url.encodedPath.endsWith("/comando") ->
                    respond(
                        """{"comandoId":"c1","acao":"ESQUECER_REDE","duracaoS":0,"emitidoEm":"2026-10-15T21:00:00Z"}""",
                        comandoStatus, jsonHeaders,
                    )
                else -> respond("{}", HttpStatusCode.OK, jsonHeaders)
            }
        }
        val http = HttpClient(engine, sharedHttpClientConfig("https://api.cafey.test", tokenStorage))
        val api = ApiClient("https://api.cafey.test", tokenStorage, http)
        return DeviceApi(api) to CommandApi(api)
    }

    private fun TestScope.viewModel(
        deviceApi: DeviceApi,
        commandApi: CommandApi? = null,
        bleClient: FakeBleClient? = null,
    ) = BaseViewModel(
        deviceApi = deviceApi,
        themePreference = ThemePreference(MemoryThemeStorage()),
        activeDeviceId = null,
        coroutineScope = backgroundScope,
        commandApi = commandApi,
        bleClient = bleClient,
    )

    @Test
    fun listarRedesWifi_carregaRedeAtualELista() = runTest(testDispatcher) {
        val (deviceApi, commandApi) = apis()
        val ble = FakeBleClient(
            redesWifiRetorno = WifiNetworksInfo(
                ssid_atual = "Casa",
                redes = listOf("Casa", "Escritorio", "Celular"),
            ),
        )
        val vm = viewModel(deviceApi, commandApi, ble)

        vm.carregarRedesWifi("ble-1")
        val state = vm.uiState.first { !it.wifiCarregando && it.wifiRedes != null }

        assertNull(state.wifiErro)
        assertEquals("Casa", state.wifiRedes?.ssid_atual)
        assertEquals(listOf("Casa", "Escritorio", "Celular"), state.wifiRedes?.redes)
    }

    @Test
    fun confirmarExclusao_requerConfirmacaoAntesDeExecutar() = runTest(testDispatcher) {
        val (deviceApi, commandApi) = apis()
        val ble = FakeBleClient(
            redesWifiRetorno = WifiNetworksInfo(ssid_atual = "Casa", redes = listOf("Casa", "B")),
        )
        val vm = viewModel(deviceApi, commandApi, ble)
        vm.carregarRedesWifi("ble-1")
        vm.uiState.first { it.wifiRedes != null }

        vm.solicitarEsquecerRede("Casa")
        assertEquals("Casa", vm.uiState.value.confirmarEsquecimento)
        assertTrue(ble.redesEsquecidas.isEmpty())

        vm.cancelarConfirmacao()
        assertNull(vm.uiState.value.confirmarEsquecimento)
        assertTrue(ble.redesEsquecidas.isEmpty())

        vm.solicitarEsquecerRede("Casa")
        vm.confirmarEsquecimento("ble-1")
        val final = vm.uiState.first { !it.wifiOperacaoEmAndamento && it.wifiRedes?.redes == listOf("B") }

        assertNull(final.confirmarEsquecimento)
        assertEquals(listOf("ble-1" to "Casa"), ble.redesEsquecidas)
        assertEquals("B", final.wifiRedes?.ssid_atual)
        assertEquals(listOf("B"), final.wifiRedes?.redes)
    }

    @Test
    fun esquecerTodas_pedeConfirmacaoEExecuta() = runTest(testDispatcher) {
        val (deviceApi, commandApi) = apis()
        val ble = FakeBleClient(
            redesWifiRetorno = WifiNetworksInfo(ssid_atual = "Casa", redes = listOf("Casa")),
        )
        val vm = viewModel(deviceApi, commandApi, ble)
        vm.carregarRedesWifi("ble-1")
        vm.uiState.first { it.wifiRedes != null }

        vm.solicitarEsquecerTodas()
        assertEquals(CONFIRMAR_ESQUECER_TODAS, vm.uiState.value.confirmarEsquecimento)

        vm.confirmarEsquecimento("ble-1")
        val final = vm.uiState.first { !it.wifiOperacaoEmAndamento && it.wifiRedes?.redes?.isEmpty() == true }

        assertEquals(1, ble.esqueceuTodas)
        assertEquals(emptyList(), final.wifiRedes?.redes)
        assertNull(final.wifiRedes?.ssid_atual)
    }

    @Test
    fun listarRedesWifi_trataErroBle() = runTest(testDispatcher) {
        val (deviceApi, commandApi) = apis()
        val ble = FakeBleClient(erroAoListarRedes = BleError.Timeout("sem resposta"))
        val vm = viewModel(deviceApi, commandApi, ble)

        vm.carregarRedesWifi("ble-1")
        val state = vm.uiState.first { !it.wifiCarregando && it.wifiErro != null }

        assertNull(state.wifiRedes)
        assertTrue(state.wifiErro?.contains("Tempo esgotado") == true)
    }

    @Test
    fun esquecerSemBleEOffline_informaNecessidadeDeBluetooth() = runTest(testDispatcher) {
        val (deviceApi, commandApi) = apis(deviceJson(online = false))
        val vm = viewModel(deviceApi, commandApi, bleClient = null)
        vm.loadDevice()
        vm.uiState.first { !it.loading }

        vm.solicitarEsquecerRede("Casa")
        vm.confirmarEsquecimento(bleDispositivoId = null)
        val state = vm.uiState.first { !it.wifiOperacaoEmAndamento && it.wifiErro != null }

        assertTrue(state.wifiErro?.contains("offline", ignoreCase = true) == true)
        assertTrue(state.wifiErro?.contains("Bluetooth", ignoreCase = true) == true)
    }

    @Test
    fun esquecerViaNuvem_trataErroDaApi() = runTest(testDispatcher) {
        val (deviceApi, commandApi) = apis(deviceJson(online = true), comandoStatus = HttpStatusCode.Unauthorized)
        val vm = viewModel(deviceApi, commandApi, bleClient = null)
        vm.loadDevice()
        vm.uiState.first { !it.loading }

        vm.solicitarEsquecerTodas()
        vm.confirmarEsquecimento(bleDispositivoId = null)
        val state = vm.uiState.first { !it.wifiOperacaoEmAndamento && it.wifiErro != null }

        assertTrue(state.wifiErro != null)
    }
}
