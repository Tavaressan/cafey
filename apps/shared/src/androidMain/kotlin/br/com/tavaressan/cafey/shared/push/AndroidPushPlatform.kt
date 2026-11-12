package br.com.tavaressan.cafey.shared.push

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging

object AndroidPushPlatform {
    private const val TAG = "AndroidPushPlatform"
    private const val PREFS = "cafey_push"
    private const val KEY_LAST_TOKEN = "last_fcm_token"

    @Volatile
    var apiKey: String = ""
        private set

    @Volatile
    var appId: String = ""
        private set

    @Volatile
    var projectId: String = ""
        private set

    @Volatile
    var gcmSenderId: String = ""
        private set

    @Volatile
    private var initialized = false

    @Volatile
    var cachedToken: String? = null
        private set

    fun configure(apiKey: String, appId: String, projectId: String, gcmSenderId: String) {
        this.apiKey = apiKey
        this.appId = appId
        this.projectId = projectId
        this.gcmSenderId = gcmSenderId
    }

    fun isConfigured(): Boolean =
        apiKey.isNotBlank() && appId.isNotBlank() && projectId.isNotBlank() && gcmSenderId.isNotBlank()

    @Synchronized
    fun initializeFirebase(context: Context) {
        if (initialized) return
        if (!isConfigured()) {
            Log.i(TAG, "Firebase não configurado; push Android desativado.")
            initialized = true
            return
        }
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApiKey(apiKey)
                    .setApplicationId(appId)
                    .setProjectId(projectId)
                    .setGcmSenderId(gcmSenderId)
                    .build()
                FirebaseApp.initializeApp(context, options)
            }
            Log.i(TAG, "Firebase inicializado para push.")
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao inicializar Firebase; push desativado.", e)
        }
        initialized = true
    }

    fun updateToken(context: Context?, token: String?) {
        cachedToken = token
        if (context != null && token != null) {
            try {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_LAST_TOKEN, token).apply()
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao persistir token FCM.", e)
            }
        }
    }

    fun loadPersistedToken(context: Context): String? {
        return try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST_TOKEN, null)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao ler token FCM persistido.", e)
            null
        }
    }

    fun fetchTokenAsync(context: Context, onResult: (String?) -> Unit) {
        initializeFirebase(context)
        val persisted = loadPersistedToken(context)
        if (persisted != null) cachedToken = persisted
        if (!isConfigured()) {
            onResult(cachedToken)
            return
        }
        try {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    updateToken(context, token)
                    onResult(token)
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Falha ao obter token FCM.", e)
                    onResult(cachedToken ?: persisted)
                }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase indisponível ao obter token.", e)
            onResult(cachedToken ?: persisted)
        }
    }
}
