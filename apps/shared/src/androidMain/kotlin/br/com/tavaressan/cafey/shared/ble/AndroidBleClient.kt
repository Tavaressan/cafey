package br.com.tavaressan.cafey.shared.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import br.com.tavaressan.cafey.shared.auth.AndroidPlatformContext
import br.com.tavaressan.cafey.shared.domain.model.AcaoComando
import br.com.tavaressan.cafey.shared.domain.model.BleEvento
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual fun createPlatformBleClient(): BleClient? = try {
    AndroidBleClient(AndroidPlatformContext.require())
} catch (_: Throwable) {
    null
}

/**
 * Implementação nativa do cliente BLE para plataforma Android (UC-04 #249, spec §6.4/§6.5).
 */
class AndroidBleClient(private val context: Context) : BleClient {

    private val json = Json { ignoreUnknownKeys = true }

    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    override val isSupported: Boolean
        get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) &&
            bluetoothAdapter != null

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(android.Manifest.permission.BLUETOOTH_SCAN) &&
                hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            hasPermission(android.Manifest.permission.BLUETOOTH) &&
                hasPermission(android.Manifest.permission.BLUETOOTH_ADMIN)
        }
    }

    @SuppressLint("MissingPermission")
    override fun escanearDispositivos(): Flow<List<BleDispositivo>> = callbackFlow {
        if (!isSupported || !hasBluetoothPermissions()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null || bluetoothAdapter?.isEnabled != true) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val discovered = ConcurrentHashMap<String, BleDispositivo>()
        val serviceUuid = ParcelUuid(UUID.fromString(BleUuids.SERVICE))

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                val dev = result?.device ?: return
                val name = dev.name ?: result.scanRecord?.deviceName ?: "Cafey"
                val serviceUuids = result.scanRecord?.serviceUuids ?: emptyList()
                val isTarget = serviceUuids.contains(serviceUuid) ||
                    name.contains("cafey", ignoreCase = true)

                if (isTarget) {
                    discovered[dev.address] = BleDispositivo(
                        id = dev.address,
                        nome = name,
                        rssi = result.rssi,
                    )
                    trySend(discovered.values.toList().sortedByDescending { it.rssi })
                }
            }

            override fun onScanFailed(errorCode: Int) {
                close(BleError.ConexaoFalhou(RuntimeException("Falha no scan BLE: código $errorCode")))
            }
        }

        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(serviceUuid).build(),
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(filters, settings, scanCallback)
        } catch (_: Exception) {
            try {
                scanner.startScan(scanCallback)
            } catch (e2: Exception) {
                close(BleError.ConexaoFalhou(e2))
                return@callbackFlow
            }
        }

        awaitClose {
            try {
                scanner.stopScan(scanCallback)
            } catch (_: Exception) {}
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun <T> withGattSession(
        address: String,
        block: suspend (AndroidGattSession) -> T,
    ): T = withContext(Dispatchers.Main) {
        if (!hasBluetoothPermissions()) {
            throw BleError.ConexaoFalhou(SecurityException("Permissões de Bluetooth não concedidas"))
        }

        val adapter = bluetoothAdapter ?: throw BleError.BaseNaoEncontrada
        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            throw BleError.BaseNaoEncontrada
        }

        var lastException: Throwable? = null
        val maxAttempts = 2

        for (attempt in 1..maxAttempts) {
            val connectDeferred = CompletableDeferred<BluetoothGatt>()
            var activeSession: AndroidGattSession? = null
            var discoveryStarted = false

            val callback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                        // Sem pedir MTU o ATT fica em 23 e o ESP32 corta cada notificação em
                        // 20 bytes, menos que os JSONs de status Wi-Fi.
                        if (!gatt.requestMtu(517)) {
                            discoveryStarted = true
                            gatt.discoverServices()
                        }
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                        if (!connectDeferred.isCompleted) {
                            connectDeferred.completeExceptionally(
                                BleError.ConexaoFalhou(RuntimeException("GATT disconnect status $status (newState=$newState)"))
                            )
                        }
                        activeSession?.onDisconnected(status)
                    }
                }

                override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                    if (!discoveryStarted) {
                        discoveryStarted = true
                        gatt.discoverServices()
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        connectDeferred.complete(gatt)
                    } else {
                        connectDeferred.completeExceptionally(
                            BleError.ConexaoFalhou(RuntimeException("discoverServices status $status"))
                        )
                    }
                }

                override fun onCharacteristicRead(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray,
                    status: Int,
                ) {
                    activeSession?.onCharRead(characteristic.uuid, status, value)
                }

                @Deprecated("Deprecated in Java")
                override fun onCharacteristicRead(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int,
                ) {
                    @Suppress("DEPRECATION")
                    val value = characteristic.value ?: ByteArray(0)
                    activeSession?.onCharRead(characteristic.uuid, status, value)
                }

                override fun onCharacteristicWrite(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int,
                ) {
                    activeSession?.onCharWrite(characteristic.uuid, status)
                }

                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray,
                ) {
                    activeSession?.onCharChanged(characteristic.uuid, value)
                }

                @Deprecated("Deprecated in Java")
                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                ) {
                    @Suppress("DEPRECATION")
                    val value = characteristic.value ?: ByteArray(0)
                    activeSession?.onCharChanged(characteristic.uuid, value)
                }
            }

            val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, callback)
            }

            if (gatt == null) {
                lastException = BleError.ConexaoFalhou(RuntimeException("connectGatt retornou null"))
                if (attempt < maxAttempts) {
                    delay(500)
                    continue
                } else {
                    throw lastException
                }
            }

            try {
                val connectedGatt = withTimeoutOrNull(10_000) { connectDeferred.await() }
                    ?: throw BleError.Timeout("Tempo limite ao conectar por BLE à base (tentativa $attempt)")

                val session = AndroidGattSession(connectedGatt)
                activeSession = session
                return@withContext block(session)
            } catch (e: Throwable) {
                lastException = e
                try {
                    gatt.disconnect()
                    gatt.close()
                } catch (_: Exception) {}

                if (attempt < maxAttempts) {
                    delay(500)
                } else {
                    throw e
                }
            } finally {
                try {
                    gatt.disconnect()
                    gatt.close()
                } catch (_: Exception) {}
            }
        }

        throw lastException ?: BleError.ConexaoFalhou(RuntimeException("Falha desconhecida na conexão GATT"))
    }

    override suspend fun lerInfoDispositivo(dispositivoId: String): DispositivoInfo {
        return withGattSession(dispositivoId) { session ->
            val jsonStr = try {
                session.readCharacteristic(UUID.fromString(BleUuids.DEVICE_INFO))
            } catch (_: Throwable) {
                null
            }
            if (!jsonStr.isNullOrBlank()) {
                try {
                    json.decodeFromString<DispositivoInfo>(jsonStr)
                } catch (_: Exception) {
                    DispositivoInfo(serial = dispositivoId)
                }
            } else {
                DispositivoInfo(serial = dispositivoId)
            }
        }
    }

    override suspend fun provisionarWifi(
        dispositivoId: String,
        ssid: String,
        password: String,
    ): Flow<WifiProvisionStatus> = callbackFlow {
        val payload = json.encodeToString(WifiProvisionPayload(ssid, password))

        val job = launch {
            try {
                withGattSession(dispositivoId) { session ->
                    val statusJob = launch {
                        session.observeNotifications(UUID.fromString(BleUuids.WIFI_STATUS))
                            .collect { jsonStr ->
                                try {
                                    val status = json.decodeFromString<WifiProvisionStatus>(jsonStr)
                                    trySend(status)
                                    if (status.state == WifiProvisionState.CONNECTED ||
                                        status.state == WifiProvisionState.AUTH_FAILED ||
                                        status.state == WifiProvisionState.TIMEOUT ||
                                        status.state == WifiProvisionState.INVALID_PAYLOAD
                                    ) {
                                        close()
                                    }
                                } catch (_: Exception) {}
                            }
                    }

                    // Aguarda subscrição das notificações ser registrada no periférico
                    delay(250)

                    // Escreve as credenciais Wi-Fi
                    session.writeCharacteristic(UUID.fromString(BleUuids.WIFI_PROVISION), payload)
                    statusJob.join()
                }
            } catch (e: Throwable) {
                close(e)
            }
        }

        awaitClose {
            job.cancel()
        }
    }

    override suspend fun enviarComando(dispositivoId: String, acao: AcaoComando) {
        val payload = """{"acao":"${acao.name}"}"""
        withGattSession(dispositivoId) { session ->
            session.writeCharacteristic(UUID.fromString(BleUuids.COMMAND), payload)
        }
    }

    override suspend fun lerEventosPendentes(dispositivoId: String): List<BleEvento> {
        return withGattSession(dispositivoId) { session ->
            val jsonStr = session.readCharacteristic(UUID.fromString(BleUuids.PENDING_EVENTS))
            try {
                json.decodeFromString<List<BleEvento>>(jsonStr)
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    override suspend fun confirmarEventosEntregues(dispositivoId: String, eventoIds: List<String>) {
        val payload = """{"confirmados":[${eventoIds.joinToString(",")}]}"""
        withGattSession(dispositivoId) { session ->
            session.writeCharacteristic(UUID.fromString(BleUuids.CONFIRM_EVENTS), payload)
        }
    }
}

/**
 * Sessão ativa de operações GATT síncronas/assíncronas no Android.
 */
@SuppressLint("MissingPermission")
class AndroidGattSession(
    private val gatt: BluetoothGatt,
    private val serviceUuid: UUID = UUID.fromString(BleUuids.SERVICE),
) {
    private var readCallback: ((UUID, Int, ByteArray) -> Unit)? = null
    private var writeCallback: ((UUID, Int) -> Unit)? = null
    private var notifyCallback: ((UUID, ByteArray) -> Unit)? = null

    fun onCharRead(uuid: UUID, status: Int, value: ByteArray) {
        readCallback?.invoke(uuid, status, value)
    }

    fun onCharWrite(uuid: UUID, status: Int) {
        writeCallback?.invoke(uuid, status)
    }

    fun onCharChanged(uuid: UUID, value: ByteArray) {
        notifyCallback?.invoke(uuid, value)
    }

    fun onDisconnected(status: Int) {
        // Notifica pendências se houver
    }

    suspend fun readCharacteristic(charUuid: UUID): String = withTimeoutOrNull(5000) {
        val service = gatt.getService(serviceUuid) ?: gatt.services.firstOrNull { it.uuid == serviceUuid }
            ?: throw BleError.BaseNaoEncontrada("Serviço $serviceUuid não encontrado")
        val char = service.getCharacteristic(charUuid) ?: service.characteristics.firstOrNull { it.uuid == charUuid }
            ?: throw BleError.BaseNaoEncontrada("Característica $charUuid não encontrada no firmware")

        suspendCancellableCoroutine { cont ->
            readCallback = { uuid, status, value ->
                if (uuid == charUuid) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        cont.resume(value.decodeToString())
                    } else {
                        cont.resumeWithException(BleError.ConexaoFalhou(RuntimeException("GATT read error $status")))
                    }
                }
            }
            gatt.readCharacteristic(char)
        }
    } ?: throw BleError.Timeout("Timeout na leitura da característica $charUuid")

    suspend fun writeCharacteristic(charUuid: UUID, payload: String): Unit = withTimeoutOrNull(5000) {
        val service = gatt.getService(serviceUuid) ?: gatt.services.firstOrNull { it.uuid == serviceUuid }
            ?: throw BleError.BaseNaoEncontrada("Serviço $serviceUuid não encontrado")
        val char = service.getCharacteristic(charUuid) ?: service.characteristics.firstOrNull { it.uuid == charUuid }
            ?: throw BleError.BaseNaoEncontrada("Característica $charUuid não encontrada no firmware")
        val bytes = payload.encodeToByteArray()

        suspendCancellableCoroutine { cont ->
            writeCallback = { uuid, status ->
                if (uuid == charUuid) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        cont.resume(Unit)
                    } else {
                        cont.resumeWithException(BleError.ConexaoFalhou(RuntimeException("GATT write error $status")))
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(char, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION")
                char.value = bytes
                @Suppress("DEPRECATION")
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(char)
            }
        }
    } ?: throw BleError.Timeout("Timeout na escrita da característica $charUuid")

    fun observeNotifications(charUuid: UUID): Flow<String> = callbackFlow {
        val service = gatt.getService(serviceUuid) ?: gatt.services.firstOrNull { it.uuid == serviceUuid } ?: run {
            close(BleError.BaseNaoEncontrada("Serviço $serviceUuid não encontrado"))
            return@callbackFlow
        }
        val char = service.getCharacteristic(charUuid) ?: service.characteristics.firstOrNull { it.uuid == charUuid } ?: run {
            close(BleError.BaseNaoEncontrada("Característica de status Wi-Fi ($charUuid) não encontrada no firmware"))
            return@callbackFlow
        }

        notifyCallback = { uuid, value ->
            if (uuid == charUuid) {
                trySend(value.decodeToString())
            }
        }

        gatt.setCharacteristicNotification(char, true)
        val cccd = char.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
        if (cccd != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(cccd)
            }
        }

        awaitClose {
            try {
                gatt.setCharacteristicNotification(char, false)
            } catch (_: Exception) {}
        }
    }
}
