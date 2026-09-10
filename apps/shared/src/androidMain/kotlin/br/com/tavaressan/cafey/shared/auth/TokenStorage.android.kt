package br.com.tavaressan.cafey.shared.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PREFS_NAME = "cafey_secure_tokens"
private const val KEY_ACCESS = "access_token"
private const val KEY_REFRESH = "refresh_token"

actual fun createTokenStorage(): TokenStorage = AndroidTokenStorage(AndroidPlatformContext.require())

class AndroidTokenStorage(private val context: Context) : TokenStorage {

    private fun prefs() = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override suspend fun save(tokens: StoredTokens) = withContext(Dispatchers.IO) {
        prefs().edit()
            .putString(KEY_ACCESS, tokens.accessToken)
            .putString(KEY_REFRESH, tokens.refreshToken)
            .apply()
    }

    override suspend fun load(): StoredTokens? = withContext(Dispatchers.IO) {
        val p = prefs()
        val access = p.getString(KEY_ACCESS, null)
        val refresh = p.getString(KEY_REFRESH, null)
        if (access != null && refresh != null) StoredTokens(access, refresh) else null
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        prefs().edit().clear().apply()
    }
}
