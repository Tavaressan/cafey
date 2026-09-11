package br.com.tavaressan.cafey.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import br.com.tavaressan.cafey.shared.App
import br.com.tavaressan.cafey.shared.auth.AndroidPlatformContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // O TokenStorage do Android depende do Context; precisa existir antes do AppContainer.
        AndroidPlatformContext.init(applicationContext)

        setContent {
            App(baseUrl = BuildConfig.BACKEND_BASE_URL)
        }
    }
}
