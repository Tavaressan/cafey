package br.com.tavaressan.cafey.shared.ui.push

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme

@Composable
actual fun PushNotificationsSection(modifier: Modifier) {
    val context = LocalContext.current
    val estado = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (granted) "Notificações: permitidas no sistema (quando configuradas)." else "Notificações: permissão ainda não concedida (será solicitada em momento contextual)."
        }
        else -> "Notificações: permitidas pelo sistema nesta versão do Android."
    }
    Column(modifier = modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(
            "Notificações",
            style = CafeyTheme.typography.cardTitle,
            color = CafeyTheme.colors.ink,
        )
        Text(
            estado,
            style = CafeyTheme.typography.bodySmall,
            color = CafeyTheme.colors.muted,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
