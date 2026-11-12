package br.com.tavaressan.cafey.android.push

import android.util.Log
import br.com.tavaressan.cafey.android.BuildConfig
import br.com.tavaressan.cafey.shared.auth.AndroidPlatformContext
import br.com.tavaressan.cafey.shared.auth.createTokenStorage
import br.com.tavaressan.cafey.shared.network.ApiClient
import br.com.tavaressan.cafey.shared.network.PushApi
import br.com.tavaressan.cafey.shared.push.AndroidPushPlatform
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CafeyFirebaseMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "Novo token FCM recebido.")
        AndroidPushPlatform.updateToken(applicationContext, token)
        // Registra o token se houver sessão ativa.
        scope.launch {
            try {
                AndroidPlatformContext.init(applicationContext)
                val tokenStorage = createTokenStorage()
                val stored = tokenStorage.load() ?: return@launch
                if (stored.accessToken.isBlank()) return@launch
                val apiClient = ApiClient(BuildConfig.BACKEND_BASE_URL, tokenStorage)
                val pushApi = PushApi(apiClient)
                pushApi.registrarAndroid(token)
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao registrar novo token FCM (não crítico).", e)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        CafeyPush.initialize(applicationContext)
        val data = message.data
        if (data.isNotEmpty()) {
            CafeyPush.exibirNotificacao(applicationContext, data)
        }
    }

    companion object {
        private const val TAG = "CafeyFcmService"
    }
}
