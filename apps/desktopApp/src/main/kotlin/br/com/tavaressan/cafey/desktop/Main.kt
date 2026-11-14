package br.com.tavaressan.cafey.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import br.com.tavaressan.cafey.shared.App
import br.com.tavaressan.cafey.shared.AppContainer

fun main() = application {
    val container = remember { AppContainer(desktopNotificationsEnabled = true) }
    val trayState = rememberTrayState()
    val icon = painterResource("icon.png")
    val traySupported = remember { java.awt.SystemTray.isSupported() }

    var isWindowVisible by remember { mutableStateOf(true) }
    var keepInTrayOnClose by remember { mutableStateOf(true) }
    var pendingFocusDeviceId by remember { mutableStateOf<String?>(null) }
    var composeWindow by remember { mutableStateOf<ComposeWindow?>(null) }

    val notifier = remember(trayState) {
        DesktopNotifier(
            isTraySupported = { traySupported },
            traySender = { title, body ->
                trayState.sendNotification(
                    Notification(title = title, message = body, type = Notification.Type.Info),
                )
            },
        )
    }

    fun bringToFront() {
        try {
            composeWindow?.apply {
                isVisible = true
                toFront()
                requestFocus()
            }
        } catch (_: Throwable) {
            // best-effort
        }
    }

    fun showAndFocus(deviceId: String? = null) {
        if (deviceId != null) {
            pendingFocusDeviceId = deviceId
            container.selectDevice(deviceId)
        }
        isWindowVisible = true
        bringToFront()
    }

    fun exitApp() {
        exitApplication()
    }

    // Consome notificações emitidas pela detecção de transições do polling (app aberto/minimizado).
    LaunchedEffect(container, notifier) {
        container.desktopNotifications.collect { event ->
            pendingFocusDeviceId = event.deviceId
            notifier.notify(event.title, event.body)
        }
    }

    // Ao reabrir a janela, seleciona o dispositivo da última notificação e foca a própria janela.
    LaunchedEffect(isWindowVisible, pendingFocusDeviceId, composeWindow) {
        if (isWindowVisible) {
            pendingFocusDeviceId?.let { container.selectDevice(it) }
            bringToFront()
        }
    }

    if (traySupported) {
        Tray(
            state = trayState,
            icon = icon,
            tooltip = "Caféy",
            onAction = { showAndFocus(pendingFocusDeviceId) },
            menu = {
                Item("Abrir Caféy", onClick = { showAndFocus(pendingFocusDeviceId) })
                Item(
                    if (keepInTrayOnClose) "Fechar janela mantém na bandeja: ligado" else "Fechar janela mantém na bandeja: desligado",
                    onClick = { keepInTrayOnClose = !keepInTrayOnClose },
                )
                Separator()
                Item("Sair", onClick = ::exitApp)
            },
        )
    }

    Window(
        onCloseRequest = {
            if (notifier.shouldExitOnClose(keepInTrayOnClose)) {
                exitApp()
            } else {
                isWindowVisible = false
            }
        },
        visible = isWindowVisible,
        title = "Caféy",
        icon = icon,
    ) {
        composeWindow = window
        App(customContainer = container)
    }
}
