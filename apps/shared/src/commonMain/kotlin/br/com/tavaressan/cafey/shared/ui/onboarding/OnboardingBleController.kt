package br.com.tavaressan.cafey.shared.ui.onboarding

import androidx.compose.runtime.Composable

/** Ações de plataforma para a etapa 3 (permissões e Bluetooth) — implementadas por plataforma. */
interface OnboardingBleController {
    val status: OnboardingBleStatus

    /** Solicita as permissões de Bluetooth/localização necessárias, explicadas previamente na UI. */
    fun requestPermissions()

    /** Solicita ao sistema para ligar o Bluetooth (`ACTION_REQUEST_ENABLE` no Android). Nunca liga silenciosamente. */
    fun requestBluetoothEnable()

    /** Abre as configurações do app (útil em "não perguntar novamente"). */
    fun openAppSettings()

    /** Abre as configurações de Bluetooth do sistema. */
    fun openBluetoothSettings()

    /** Recalcula o estado atual (ex.: ao voltar do app de Configurações). */
    fun refresh()
}

@Composable
expect fun rememberOnboardingBleController(
    onStatusChange: (OnboardingBleStatus) -> Unit,
): OnboardingBleController
