package br.com.tavaressan.cafey.desktop

import java.io.File

enum class DesktopNotificationStrategy {
    TRAY,
    NOTIFY_SEND,
    NONE,
}

class DesktopNotifier(
    private val isTraySupported: () -> Boolean = { java.awt.SystemTray.isSupported() },
    private val isLinux: () -> Boolean = {
        System.getProperty("os.name").orEmpty().lowercase().contains("linux")
    },
    private val notifySendAvailable: () -> Boolean = {
        val path = System.getenv("PATH").orEmpty()
        path.split(File.pathSeparator).any { dir ->
            File(dir, "notify-send").canExecute()
        }
    },
    private val traySender: (title: String, body: String) -> Unit,
    private val processStarter: (List<String>) -> Unit = { command ->
        ProcessBuilder(command).start()
    },
) {
    fun canUseTray(): Boolean = isTraySupported()

    /** Quando não há bandeja do sistema, fechar a janela deve encerrar o app. */
    fun shouldExitOnClose(keepInTrayOnClose: Boolean): Boolean = !canUseTray() || !keepInTrayOnClose

    fun notify(title: String, body: String): DesktopNotificationStrategy {
        if (canUseTray()) {
            try {
                traySender(title, body)
                return DesktopNotificationStrategy.TRAY
            } catch (_: Throwable) {
                // cai para fallback abaixo
            }
        }
        if (isLinux() && notifySendAvailable()) {
            return try {
                processStarter(listOf("notify-send", "-a", "Caféy", title, body))
                DesktopNotificationStrategy.NOTIFY_SEND
            } catch (_: Throwable) {
                DesktopNotificationStrategy.NONE
            }
        }
        return DesktopNotificationStrategy.NONE
    }
}
