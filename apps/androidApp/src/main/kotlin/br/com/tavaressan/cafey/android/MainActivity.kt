package br.com.tavaressan.cafey.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import br.com.tavaressan.cafey.android.push.CafeyPush
import br.com.tavaressan.cafey.shared.App
import br.com.tavaressan.cafey.shared.auth.AndroidPlatformContext
import br.com.tavaressan.cafey.shared.push.AndroidPushPlatform
import br.com.tavaressan.cafey.shared.push.PushNavigationTarget

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // O TokenStorage e AndroidBleClient do Android dependem do Context; precisa existir antes do AppContainer.
        AndroidPlatformContext.init(applicationContext)
        AndroidPushPlatform.configure(
            apiKey = BuildConfig.FIREBASE_API_KEY,
            appId = BuildConfig.FIREBASE_APP_ID,
            projectId = BuildConfig.FIREBASE_PROJECT_ID,
            gcmSenderId = BuildConfig.FIREBASE_GCM_SENDER_ID,
        )
        CafeyPush.initialize(applicationContext)
        tratarIntentDeNotificacao(intent)

        // Issue #312: permissões de Bluetooth/localização não são mais pedidas na abertura; são
        // solicitadas na etapa 3 do onboarding, após explicar o motivo ao usuário.
        setContent {
            App(baseUrl = BuildConfig.BACKEND_BASE_URL)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        tratarIntentDeNotificacao(intent)
    }

    private fun tratarIntentDeNotificacao(intent: Intent?) {
        val dispositivoId = intent?.getStringExtra(CafeyPush.EXTRA_DISPOSITIVO_ID)
            ?: intent?.getStringExtra("dispositivoId")
        if (!dispositivoId.isNullOrBlank()) {
            PushNavigationTarget.setPendingDeviceId(dispositivoId)
        }
    }
}
