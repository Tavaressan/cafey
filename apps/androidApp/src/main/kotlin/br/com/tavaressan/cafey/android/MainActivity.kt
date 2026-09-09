package br.com.tavaressan.cafey.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import br.com.tavaressan.cafey.android.ui.CafeyNavHost
import br.com.tavaressan.cafey.shared.AppContainer
import br.com.tavaressan.cafey.shared.LocalAppContainer
import br.com.tavaressan.cafey.shared.auth.AndroidPlatformContext
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidPlatformContext.init(applicationContext)

        setContent {
            val container = remember { AppContainer() }
            CompositionLocalProvider(LocalAppContainer provides container) {
                CafeyTheme {
                    CafeyNavHost()
                }
            }
        }
    }
}
