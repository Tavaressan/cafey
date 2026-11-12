package br.com.tavaressan.cafey.shared.ui.push

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
actual fun ContextualPushPermissionEffect(shouldRequest: Boolean, onConsumed: () -> Unit) {
    LaunchedEffect(shouldRequest) {
        if (shouldRequest) onConsumed()
    }
}
