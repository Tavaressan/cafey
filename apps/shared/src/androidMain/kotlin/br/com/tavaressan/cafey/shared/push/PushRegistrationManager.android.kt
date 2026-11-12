package br.com.tavaressan.cafey.shared.push

import android.util.Log
import br.com.tavaressan.cafey.shared.auth.AndroidPlatformContext
import br.com.tavaressan.cafey.shared.network.ApiClient
import br.com.tavaressan.cafey.shared.network.PushApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private class AndroidPushRegistrationManager(
    private val apiClient: ApiClient,
    private val pushApi: PushApi,
) : PushRegistrationManager {

    override suspend fun registrarSePossivel() {
        if (!apiClient.isAuthenticated()) return
        if (!AndroidPushPlatform.isConfigured()) return
        val context = try {
            AndroidPlatformContext.require()
        } catch (e: Exception) {
            Log.w(TAG, "Contexto Android indisponível para registrar push.", e)
            return
        }
        val token = obterToken(context) ?: run {
            Log.i(TAG, "Sem token FCM disponível; registro de push adiado.")
            return
        }
        try {
            pushApi.registrarAndroid(token)
            Log.i(TAG, "Destino de push Android registrado.")
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao registrar destino de push Android (não crítico).", e)
        }
    }

    override suspend fun removerAntesDeLogout() {
        if (!apiClient.isAuthenticated()) return
        val context = try {
            AndroidPlatformContext.require()
        } catch (_: Exception) {
            null
        }
        val token = AndroidPushPlatform.cachedToken
            ?: context?.let { AndroidPushPlatform.loadPersistedToken(it) }
            ?: return
        try {
            pushApi.remover(token)
            Log.i(TAG, "Destino de push Android removido.")
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao remover destino de push Android (não crítico).", e)
        }
    }

    private suspend fun obterToken(context: android.content.Context): String? =
        suspendCancellableCoroutine { cont ->
            try {
                AndroidPushPlatform.fetchTokenAsync(context) { token ->
                    if (cont.isActive) cont.resume(token)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Erro ao obter token FCM.", e)
                if (cont.isActive) cont.resume(null)
            }
        }

    companion object {
        private const val TAG = "AndroidPushReg"
    }
}

actual fun createPushRegistrationManager(apiClient: ApiClient, pushApi: PushApi): PushRegistrationManager =
    AndroidPushRegistrationManager(apiClient, pushApi)
