package br.com.tavaressan.cafey.shared.ui.push

import androidx.compose.runtime.Composable

/**
 * Solicita permissão de notificações em momento contextual (Android 13+), sem insistir após recusa.
 * Plataformas sem suporte são no-op.
 */
@Composable
expect fun ContextualPushPermissionEffect(shouldRequest: Boolean, onConsumed: () -> Unit)
