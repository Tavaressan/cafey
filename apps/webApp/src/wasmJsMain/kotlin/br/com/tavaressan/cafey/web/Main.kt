package br.com.tavaressan.cafey.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import br.com.tavaressan.cafey.shared.App
import br.com.tavaressan.cafey.shared.network.platformDefaultBaseUrl
import kotlinx.browser.document

// URL do backend injetada em build-time (issue #157, equivalente Web da #147/Android) via
// `window.__CAFEY_BACKEND_URL__`, definido pelo script gerado `cafey-config.js` (ver
// generateWebBackendConfig em build.gradle.kts). Sem o global (ex.: script vazio/ausente), cai no
// padrão de desenvolvimento local.
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("() => window.__CAFEY_BACKEND_URL__ ?? null")
private external fun configuredBackendUrl(): String?

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val baseUrl = configuredBackendUrl()?.takeIf { it.isNotBlank() } ?: platformDefaultBaseUrl
    ComposeViewport(document.body!!) {
        App(baseUrl = baseUrl)
    }
}
