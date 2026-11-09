package br.com.tavaressan.cafey.shared.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

@Composable
actual fun rememberOnboardingBleController(
    onStatusChange: (OnboardingBleStatus) -> Unit,
): OnboardingBleController {
    val controller = remember {
        object : OnboardingBleController {
            override val status = OnboardingBleStatus(supported = false)
            override fun requestPermissions() = Unit
            override fun requestBluetoothEnable() = Unit
            override fun openAppSettings() = Unit
            override fun openBluetoothSettings() = Unit
            override fun refresh() = Unit
        }
    }
    LaunchedEffect(Unit) { onStatusChange(controller.status) }
    return controller
}
