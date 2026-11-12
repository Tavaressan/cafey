package br.com.tavaressan.cafey.shared.ui.push

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

private const val PREFS = "cafey_push_prefs"
private const val KEY_PROMPTED = "post_notifications_prompted"

private fun jaSolicitado(context: Context): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_PROMPTED, false)

private fun marcarSolicitado(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_PROMPTED, true).apply()
}

@Composable
actual fun ContextualPushPermissionEffect(shouldRequest: Boolean, onConsumed: () -> Unit) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        // Recusa não insiste: não faz nova tentativa automática.
        marcarSolicitado(context)
        pending = false
        onConsumed()
    }

    LaunchedEffect(shouldRequest) {
        if (!shouldRequest) return@LaunchedEffect
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onConsumed()
            return@LaunchedEffect
        }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted || jaSolicitado(context)) {
            onConsumed()
            return@LaunchedEffect
        }
        if (!pending) {
            pending = true
            marcarSolicitado(context)
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
