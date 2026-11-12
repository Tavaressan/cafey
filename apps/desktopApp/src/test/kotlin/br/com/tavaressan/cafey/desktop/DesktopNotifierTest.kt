package br.com.tavaressan.cafey.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopNotifierTest {
    @Test
    fun usaTrayQuandoDisponivel() {
        var trayCalls = 0
        val commands = mutableListOf<List<String>>()
        val notifier = DesktopNotifier(
            isTraySupported = { true },
            isLinux = { true },
            notifySendAvailable = { true },
            traySender = { _, _ -> trayCalls++ },
            processStarter = { commands += it },
        )

        val strategy = notifier.notify("Caféy Cozinha", "Seu café está pronto! Enjoy!")

        assertEquals(DesktopNotificationStrategy.TRAY, strategy)
        assertEquals(1, trayCalls)
        assertTrue(commands.isEmpty())
        assertFalse(notifier.shouldExitOnClose(keepInTrayOnClose = true))
    }

    @Test
    fun fallbackNotifySendNoLinuxQuandoTrayFalha() {
        val commands = mutableListOf<List<String>>()
        val notifier = DesktopNotifier(
            isTraySupported = { true },
            isLinux = { true },
            notifySendAvailable = { true },
            traySender = { _, _ -> throw RuntimeException("tray falhou") },
            processStarter = { commands += it },
        )

        val strategy = notifier.notify("Caféy Cozinha", "A cafeteira ficou offline")

        assertEquals(DesktopNotificationStrategy.NOTIFY_SEND, strategy)
        assertEquals(listOf(listOf("notify-send", "-a", "Caféy", "Caféy Cozinha", "A cafeteira ficou offline")), commands)
    }

    @Test
    fun fallbackNotifySendQuandoNaoHaBandeja() {
        val commands = mutableListOf<List<String>>()
        val notifier = DesktopNotifier(
            isTraySupported = { false },
            isLinux = { true },
            notifySendAvailable = { true },
            traySender = { _, _ -> error("não deve usar tray") },
            processStarter = { commands += it },
        )

        val strategy = notifier.notify("Caféy", "Preparando seu café…")

        assertEquals(DesktopNotificationStrategy.NOTIFY_SEND, strategy)
        assertEquals(1, commands.size)
        assertTrue(notifier.shouldExitOnClose(keepInTrayOnClose = true))
    }

    @Test
    fun semBandejaESemNotifySend_naoNotifica() {
        val notifier = DesktopNotifier(
            isTraySupported = { false },
            isLinux = { true },
            notifySendAvailable = { false },
            traySender = { _, _ -> },
            processStarter = { _ -> error("não deve iniciar processo") },
        )

        val strategy = notifier.notify("Caféy", "Seu café está pronto! Enjoy!")

        assertEquals(DesktopNotificationStrategy.NONE, strategy)
        assertTrue(notifier.shouldExitOnClose(keepInTrayOnClose = true))
    }

    @Test
    fun comBandeja_respeitaPreferenciaDeFechar() {
        val notifier = DesktopNotifier(
            isTraySupported = { true },
            isLinux = { true },
            notifySendAvailable = { false },
            traySender = { _, _ -> },
        )

        assertFalse(notifier.shouldExitOnClose(keepInTrayOnClose = true))
        assertTrue(notifier.shouldExitOnClose(keepInTrayOnClose = false))
    }
}
