package br.com.tavaressan.cafey.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import br.com.tavaressan.cafey.shared.App

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Caféy") {
        App()
    }
}
