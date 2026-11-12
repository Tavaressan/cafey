package br.com.tavaressan.cafey.android.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import br.com.tavaressan.cafey.android.MainActivity
import br.com.tavaressan.cafey.android.R
import br.com.tavaressan.cafey.shared.push.AndroidPushPlatform

object CafeyPush {
    private const val TAG = "CafeyPush"
    const val CHANNEL_PREPARO = "preparo"
    const val CHANNEL_STATUS = "status"
    const val EXTRA_DISPOSITIVO_ID = "cafey_dispositivo_id"
    const val EXTRA_TAG = "cafey_tag"

    @Volatile
    private var initialized = false

    fun isConfigured(): Boolean = AndroidPushPlatform.isConfigured()

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        criarCanais(context)
        AndroidPushPlatform.initializeFirebase(context)
        initialized = true
    }

    fun criarCanais(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val preparo = NotificationChannel(
            CHANNEL_PREPARO,
            "Preparo",
            NotificationManager.IMPORTANCE_HIGH,
        )
        val status = NotificationChannel(
            CHANNEL_STATUS,
            "Status da cafeteira",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(preparo)
        manager.createNotificationChannel(status)
    }

    fun obterTokenAtual(context: Context, onResult: (String?) -> Unit) {
        AndroidPushPlatform.fetchTokenAsync(context, onResult)
    }

    fun podeExibirNotificacoes(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun exibirNotificacao(context: Context, data: Map<String, String>) {
        val titulo = data["titulo"] ?: "Cafey"
        val corpo = data["corpo"] ?: ""
        val tipo = data["tipo"].orEmpty()
        val tag = data["tag"] ?: tipo
        val dispositivoId = data["dispositivoId"]
        val canal = when (tipo) {
            "CAFE_PRONTO", "FALHA_PREPARO" -> CHANNEL_PREPARO
            else -> CHANNEL_STATUS
        }

        if (!podeExibirNotificacoes(context)) {
            Log.i(TAG, "Notificação recebida sem permissão/habilitação; ignorando exibição em primeiro plano.")
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!dispositivoId.isNullOrBlank()) putExtra(EXTRA_DISPOSITIVO_ID, dispositivoId)
            if (tag.isNotBlank()) putExtra(EXTRA_TAG, tag)
        }
        val requestCode = (dispositivoId ?: tag).hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, canal)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(titulo)
            .setContentText(corpo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(corpo))
            .setPriority(
                if (canal == CHANNEL_PREPARO) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(tag.hashCode(), notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Sem permissão para exibir notificação.", e)
        }
    }
}
