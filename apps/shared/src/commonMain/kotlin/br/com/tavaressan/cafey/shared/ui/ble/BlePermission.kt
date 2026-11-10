package br.com.tavaressan.cafey.shared.ui.ble

import androidx.compose.runtime.Composable

/**
 * Estado do ambiente BLE/permissões, reportado pela plataforma.
 * Lógica de decisão (quando pedir permissão, BT desligado, "não perguntar novamente") é testável
 * em `commonTest` a partir deste modelo (issue #312).
 */
data class BlePermissionStatus(
    /** BLE/provisionamento suportado nesta plataforma (Android com adaptador). */
    val supported: Boolean = false,
    /** Permissões de Bluetooth/localização necessárias já concedidas. */
    val permissionsGranted: Boolean = false,
    /** Usuário negou e selecionou "não perguntar novamente" (ou equivalente): só resta abrir Configurações. */
    val permissionsPermanentlyDenied: Boolean = false,
    /** Adaptador Bluetooth ligado. `null` quando desconhecido/não suportado. */
    val bluetoothEnabled: Boolean? = null,
) {
    /** Pronto para iniciar ação BLE (scan/provisionamento) nesta plataforma. */
    val ready: Boolean
        get() = supported && permissionsGranted && bluetoothEnabled == true
}

/** Ações de plataforma para obter prontidão BLE (permissões e Bluetooth). */
interface BlePermissionController {
    val status: BlePermissionStatus

    /** Solicita as permissões de Bluetooth/localização necessárias (após explicação na UI). */
    fun requestPermissions()

    /** Solicita ao sistema para ligar o Bluetooth (Android: ACTION_REQUEST_ENABLE). Nunca liga silenciosamente. */
    fun requestBluetoothEnable()

    /** Abre as configurações do app (útil em "não perguntar novamente"). */
    fun openAppSettings()

    /** Abre as configurações de Bluetooth do sistema. */
    fun openBluetoothSettings()

    /** Recalcula o estado atual (ex.: ao voltar das Configurações). */
    fun refresh()
}

/**
 * Próxima ação recomendada para tornar o ambiente BLE pronto, derivada do [status].
 * Usada pela UI compartilhada e testável em `commonTest`.
 */
sealed interface BleReadinessAction {
    data object Unsupported : BleReadinessAction
    data object RequestPermissions : BleReadinessAction
    data object OpenAppSettings : BleReadinessAction
    data object EnableBluetooth : BleReadinessAction
    data object Ready : BleReadinessAction
}

fun BlePermissionStatus.readinessAction(): BleReadinessAction = when {
    !supported -> BleReadinessAction.Unsupported
    !permissionsGranted && permissionsPermanentlyDenied -> BleReadinessAction.OpenAppSettings
    !permissionsGranted -> BleReadinessAction.RequestPermissions
    bluetoothEnabled != true -> BleReadinessAction.EnableBluetooth
    else -> BleReadinessAction.Ready
}

@Composable
expect fun rememberBlePermissionController(
    onStatusChange: (BlePermissionStatus) -> Unit = {},
): BlePermissionController
