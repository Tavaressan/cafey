package br.com.tavaressan.cafey.shared.ui.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Lógica compartilhada de estado de permissão/prontidão BLE (issue #312). */
class BlePermissionReadinessTest {

    @Test
    fun unsupportedPlatformIsNotReady() {
        val status = BlePermissionStatus(supported = false, permissionsGranted = true, bluetoothEnabled = true)
        assertFalse(status.ready)
        assertEquals(BleReadinessAction.Unsupported, status.readinessAction())
    }

    @Test
    fun missingPermissionsRequestsPermission() {
        val status = BlePermissionStatus(
            supported = true,
            permissionsGranted = false,
            permissionsPermanentlyDenied = false,
            bluetoothEnabled = true,
        )
        assertFalse(status.ready)
        assertEquals(BleReadinessAction.RequestPermissions, status.readinessAction())
    }

    @Test
    fun permanentlyDeniedPermissionsOpenAppSettings() {
        val status = BlePermissionStatus(
            supported = true,
            permissionsGranted = false,
            permissionsPermanentlyDenied = true,
            bluetoothEnabled = true,
        )
        assertFalse(status.ready)
        assertEquals(BleReadinessAction.OpenAppSettings, status.readinessAction())
    }

    @Test
    fun bluetoothDisabledRequestsEnable() {
        val status = BlePermissionStatus(
            supported = true,
            permissionsGranted = true,
            permissionsPermanentlyDenied = false,
            bluetoothEnabled = false,
        )
        assertFalse(status.ready)
        assertEquals(BleReadinessAction.EnableBluetooth, status.readinessAction())
    }

    @Test
    fun bluetoothUnknownRequestsEnable() {
        val status = BlePermissionStatus(
            supported = true,
            permissionsGranted = true,
            bluetoothEnabled = null,
        )
        assertFalse(status.ready)
        assertEquals(BleReadinessAction.EnableBluetooth, status.readinessAction())
    }

    @Test
    fun grantedPermissionsAndBluetoothOnIsReady() {
        val status = BlePermissionStatus(
            supported = true,
            permissionsGranted = true,
            permissionsPermanentlyDenied = false,
            bluetoothEnabled = true,
        )
        assertTrue(status.ready)
        assertEquals(BleReadinessAction.Ready, status.readinessAction())
    }

    @Test
    fun permanentlyDeniedFlagIgnoredWhenPermissionsGranted() {
        val status = BlePermissionStatus(
            supported = true,
            permissionsGranted = true,
            permissionsPermanentlyDenied = true,
            bluetoothEnabled = true,
        )
        assertTrue(status.ready)
        assertEquals(BleReadinessAction.Ready, status.readinessAction())
    }
}
