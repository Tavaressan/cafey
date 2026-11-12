package br.com.tavaressan.cafey.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import br.com.tavaressan.cafey.shared.App
import br.com.tavaressan.cafey.shared.AppContainer
import java.awt.Window as AwtWindow

fun main() = application {
    val container = remember { AppContainer() }
    val trayState = rememberTrayState()
    val icon = painterResource("icon.png")

    var isWindowVisible by remember { mutableStateOf(true) }
    var keepInTrayOnClose by remember { mutableStateOf(true) }
    var pendingFocusDeviceId by remember { mutableStateOf<String?>(null) }

    fun showAndFocus(deviceId: String? = null) {
        if (deviceId != null) {
            pendingFocusDeviceId = deviceId
            container.selectDevice(deviceId)
        }
        isWindowVisible = true
    }

    fun exitApp() {
        exitApplication()
    }

    // Consome notificações emitidas pela detecção de transições do polling (app aberto/minimizado).
    LaunchedEffect(container) {
        container.desktopNotifications.collect { event ->
            pendingFocusDeviceId = event.deviceId
            try {
                trayState.sendNotification(
                    Notification(
                        title = event.title,
                        message = event.body,
                        type = Notification.Type.Info,
                    ),
                )
            } catch (_: Throwable) {
                // Falha ao exibir notificação nativa não deve derrubar o app.
            }
        }
    }

    // Ao reabrir a janela (inclusive pelo ícone/menu), seleciona o dispositivo da última notificação.
    LaunchedEffect(isWindowVisible, pendingFocusDeviceId) {
        if (isWindowVisible) {
            pendingFocusDeviceId?.let { container.selectDevice(it) }
            try {
                AwtWindow.getWindows()
                    .firstOrNull { it.isDisplayable }
                    ?.apply {
                        isVisible = true
                        toFront()
                        requestFocus()
                    }
            } catch (_: Throwable) {
                // best-effort
            }
        }
    }

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

    Window(
        onCloseRequest = {
            if (keepInTrayOnClose) {
                isWindowVisible = false
            } else {
                exitApp()
            }
        },
        visible = isWindowVisible,
        title = "Caféy",
        icon = icon,
    ) {
        // Ao receber foco/interação pelo clique na notificação nem sempre temos callback;
        // a seleção do dispositivo é garantida ao abrir via bandeja/menu e mantida via activeDeviceId.
        App(customContainer = container)
    }

    DisposableEffect(Unit) {
        onDispose {
            // container não possui recursos a liberar explicitamente aqui
        }
    }
}
