package br.com.tavaressan.cafey.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import br.com.tavaressan.cafey.shared.App
import br.com.tavaressan.cafey.shared.auth.AndroidPlatformContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // O TokenStorage e AndroidBleClient do Android dependem do Context; precisa existir antes do AppContainer.
        AndroidPlatformContext.init(applicationContext)

        // Issue #312: permissões de Bluetooth/localização não são mais pedidas na abertura; são
        // solicitadas na etapa 3 do onboarding, após explicar o motivo ao usuário.
        setContent {
            App(baseUrl = BuildConfig.BACKEND_BASE_URL)
        }
    }
}
