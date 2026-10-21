package br.com.tavaressan.cafey.shared.ble

import kotlinx.serialization.Serializable

/**
 * UUIDs do serviço BLE Cafey (spec §6.4 e UC-04 #249).
 */
object BleUuids {
    const val SERVICE = "6b3d0001-0b0b-4a3a-9c0e-cafe1a000000"
    const val COMMAND = "6b3d0002-0b0b-4a3a-9c0e-cafe1a000000"
    const val STATE = "6b3d0003-0b0b-4a3a-9c0e-cafe1a000000"
    const val SCHEDULE = "6b3d0004-0b0b-4a3a-9c0e-cafe1a000000"
    const val WIFI_PROVISION = "6b3d0005-0b0b-4a3a-9c0e-cafe1a000000"
    const val WIFI_STATUS = "6b3d0006-0b0b-4a3a-9c0e-cafe1a000000"
    const val DEVICE_INFO = "6b3d0007-0b0b-4a3a-9c0e-cafe1a000000"
}

/**
 * Dados de identificação do ESP32 lidos da característica kDeviceInfoUuid (UC-04, RF-01).
 */
@Serializable
data class DispositivoInfo(
    val serial: String,
    val firmware: String = "1.0.0",
    val model: String = "ESP32-WROOM-32",
)

/**
 * Estado da conexão Wi-Fi do ESP32 notificado via kWifiStatusUuid.
 */
@Serializable
enum class WifiProvisionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    AUTH_FAILED,
    TIMEOUT,
    INVALID_PAYLOAD,
}

/**
 * Status do provisionamento Wi-Fi lido/notificado via kWifiStatusUuid (UC-04, RF-01, RF-04).
 */
@Serializable
data class WifiProvisionStatus(
    val state: WifiProvisionState,
    val ip: String? = null,
    val error: String? = null,
)

/**
 * Payload de escrita de credenciais Wi-Fi enviado via kWifiProvisionUuid (UC-04, RF-02, RF-04).
 */
@Serializable
data class WifiProvisionPayload(
    val ssid: String,
    val password: String,
)

/**
 * Periférico BLE Cafey descoberto na varredura (UC-04, RF-03).
 */
data class BleDispositivo(
    val id: String,
    val nome: String,
    val rssi: Int = 0,
)
