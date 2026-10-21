package br.com.tavaressan.cafey.shared.ble

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BleProvisioningTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun dispositivoInfo_roundTrip() {
        val original = DispositivoInfo(
            serial = "cafey-proto-01",
            firmware = "1.0.0",
            model = "ESP32-WROOM-32",
        )
        val encoded = json.encodeToString(DispositivoInfo.serializer(), original)
        val decoded = json.decodeFromString(DispositivoInfo.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun dispositivoInfo_decodesEsp32Payload() {
        val payload = """
            {"serial":"cafey-proto-01","firmware":"1.0.0","model":"ESP32-WROOM-32"}
        """.trimIndent()
        val decoded = json.decodeFromString(DispositivoInfo.serializer(), payload)
        assertEquals("cafey-proto-01", decoded.serial)
        assertEquals("1.0.0", decoded.firmware)
        assertEquals("ESP32-WROOM-32", decoded.model)
    }

    @Test
    fun wifiProvisionPayload_roundTrip() {
        val payload = WifiProvisionPayload(ssid = "MinhaRede", password = "SenhaForte123")
        val encoded = json.encodeToString(WifiProvisionPayload.serializer(), payload)
        val decoded = json.decodeFromString(WifiProvisionPayload.serializer(), encoded)
        assertEquals("MinhaRede", decoded.ssid)
        assertEquals("SenhaForte123", decoded.password)
    }

    @Test
    fun wifiProvisionStatus_decodesConnectingAndConnected() {
        val connectingJson = """{"state":"CONNECTING","ip":null,"error":null}"""
        val connecting = json.decodeFromString(WifiProvisionStatus.serializer(), connectingJson)
        assertEquals(WifiProvisionState.CONNECTING, connecting.state)
        assertNull(connecting.ip)
        assertNull(connecting.error)

        val connectedJson = """{"state":"CONNECTED","ip":"192.168.1.150","error":null}"""
        val connected = json.decodeFromString(WifiProvisionStatus.serializer(), connectedJson)
        assertEquals(WifiProvisionState.CONNECTED, connected.state)
        assertEquals("192.168.1.150", connected.ip)
        assertNull(connected.error)
    }

    @Test
    fun wifiProvisionStatus_decodesAuthFailedAndTimeout() {
        val authFailedJson = """{"state":"AUTH_FAILED","ip":null,"error":"Senha incorreta"}"""
        val authFailed = json.decodeFromString(WifiProvisionStatus.serializer(), authFailedJson)
        assertEquals(WifiProvisionState.AUTH_FAILED, authFailed.state)
        assertEquals("Senha incorreta", authFailed.error)

        val timeoutJson = """{"state":"TIMEOUT","ip":null,"error":null}"""
        val timeout = json.decodeFromString(WifiProvisionStatus.serializer(), timeoutJson)
        assertEquals(WifiProvisionState.TIMEOUT, timeout.state)
    }

    @Test
    fun fakeBleClient_scanAndProvisionFlow() = runTest {
        val client = FakeBleClient(
            dispositivosEscaneados = listOf(
                BleDispositivo(id = "cafe-01", nome = "Cafey Cozinha", rssi = -55),
                BleDispositivo(id = "cafe-02", nome = "Cafey Sala", rssi = -75),
            ),
            infoDispositivoRetorno = DispositivoInfo(serial = "cafey-serial-123"),
            statusProvisionamentoRetorno = listOf(
                WifiProvisionStatus(WifiProvisionState.CONNECTING),
                WifiProvisionStatus(WifiProvisionState.CONNECTED, ip = "192.168.1.80"),
            ),
        )

        val devices = client.escanearDispositivos().toList().first()
        assertEquals(2, devices.size)
        assertEquals("cafe-01", devices[0].id)
        assertEquals("Cafey Cozinha", devices[0].nome)

        val info = client.lerInfoDispositivo("cafe-01")
        assertEquals("cafey-serial-123", info.serial)

        val statuses = client.provisionarWifi("cafe-01", "MinhaRede", "123456").toList()
        assertEquals(2, statuses.size)
        assertEquals(WifiProvisionState.CONNECTING, statuses[0].state)
        assertEquals(WifiProvisionState.CONNECTED, statuses[1].state)
        assertEquals("192.168.1.80", statuses[1].ip)

        assertEquals(1, client.credenciaisProvisionadas.size)
        assertEquals(Triple("cafe-01", "MinhaRede", "123456"), client.credenciaisProvisionadas[0])
    }

    @Test
    fun fakeBleClient_handlesConnectionError() = runTest {
        val client = FakeBleClient(falharAoConectar = true)
        assertFailsWith<BleError.BaseNaoEncontrada> {
            client.lerInfoDispositivo("cafe-01")
        }
        assertFailsWith<BleError.BaseNaoEncontrada> {
            client.provisionarWifi("cafe-01", "SSID", "PASS")
        }
    }
}
