package br.com.tavaressan.cafey.shared.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BleDeviceFilterTest {

    @Test
    fun isTargetDevice_matchesNameWithCafeyCaseInsensitive() {
        assertTrue(BleDeviceFilter.isTargetDevice("Cafey"))
        assertTrue(BleDeviceFilter.isTargetDevice("cafey"))
        assertTrue(BleDeviceFilter.isTargetDevice("CAFEY"))
        assertTrue(BleDeviceFilter.isTargetDevice("Cafey Cozinha"))
        assertTrue(BleDeviceFilter.isTargetDevice("cafey-esp32-01"))
        assertTrue(BleDeviceFilter.isTargetDevice("Minha cafeteira Cafey"))
    }

    @Test
    fun isTargetDevice_rejectsUnrelatedNamesWithoutServiceUuid() {
        assertFalse(BleDeviceFilter.isTargetDevice("Fone Bluetooth"))
        assertFalse(BleDeviceFilter.isTargetDevice("Smart Watch"))
        assertFalse(BleDeviceFilter.isTargetDevice("ESP32-Generic"))
        assertFalse(BleDeviceFilter.isTargetDevice(null))
        assertFalse(BleDeviceFilter.isTargetDevice(""))
        assertFalse(BleDeviceFilter.isTargetDevice("   "))
    }

    @Test
    fun isTargetDevice_matchesTargetServiceUuidRegardlessOfCase() {
        val targetUuid = BleUuids.SERVICE
        assertTrue(BleDeviceFilter.isTargetDevice(null, listOf(targetUuid)))
        assertTrue(BleDeviceFilter.isTargetDevice("", listOf(targetUuid.uppercase())))
        assertTrue(BleDeviceFilter.isTargetDevice("Dispositivo Desconhecido", listOf(targetUuid.lowercase())))
        assertTrue(
            BleDeviceFilter.isTargetDevice(
                null,
                listOf("0000180f-0000-1000-8000-00805f9b34fb", targetUuid)
            )
        )
    }

    @Test
    fun isTargetDevice_rejectsUnrelatedServiceUuidsWhenNameDoesNotMatch() {
        assertFalse(
            BleDeviceFilter.isTargetDevice(
                null,
                listOf("0000180f-0000-1000-8000-00805f9b34fb", "0000180a-0000-1000-8000-00805f9b34fb")
            )
        )
        assertFalse(
            BleDeviceFilter.isTargetDevice(
                "Headphones",
                listOf("0000180f-0000-1000-8000-00805f9b34fb")
            )
        )
    }

    @Test
    fun resolveDisplayName_usesTrimmedNameWhenProvided() {
        assertEquals("Cafey", BleDeviceFilter.resolveDisplayName("Cafey"))
        assertEquals("Cafey Cozinha", BleDeviceFilter.resolveDisplayName("  Cafey Cozinha  "))
        assertEquals("cafey-001", BleDeviceFilter.resolveDisplayName("cafey-001"))
    }

    @Test
    fun resolveDisplayName_fallsBackToCafeyDefaultWhenNullOrBlank() {
        assertEquals("Cafey", BleDeviceFilter.resolveDisplayName(null))
        assertEquals("Cafey", BleDeviceFilter.resolveDisplayName(""))
        assertEquals("Cafey", BleDeviceFilter.resolveDisplayName("   "))
    }
}
