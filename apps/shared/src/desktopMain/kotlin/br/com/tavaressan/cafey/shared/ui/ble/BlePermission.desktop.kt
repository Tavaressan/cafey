package br.com.tavaressan.cafey.shared.ui.ble

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

@Composable
actual fun rememberBlePermissionController(
    onStatusChange: (BlePermissionStatus) -> Unit,
): BlePermissionController {
    val controller = remember {
        object : BlePermissionController {
            override val status = BlePermissionStatus(supported = false)
            override fun requestPermissions() = Unit
            override fun requestBluetoothEnable() = Unit
            override fun openAppSettings() = Unit
            override fun openBluetoothSettings() = Unit
            override fun refresh() = Unit
        }
    }
    LaunchedEffect(Unit) { onStatusChange(controller.status) }
    return controller
}
