package br.com.tavaressan.cafey.shared.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Dispositivo alvo vindo de toque em notificação (Android/Web). */
object PushNavigationTarget {
    private val _pendingDeviceId = MutableStateFlow<String?>(null)
    val pendingDeviceId: StateFlow<String?> = _pendingDeviceId.asStateFlow()

    fun setPendingDeviceId(id: String?) {
        _pendingDeviceId.value = id?.takeIf { it.isNotBlank() }
    }

    fun consume(): String? {
        val id = _pendingDeviceId.value
        _pendingDeviceId.value = null
        return id
    }
}
