package br.com.tavaressan.cafey.shared.preferences

import android.content.Context
import br.com.tavaressan.cafey.shared.auth.AndroidPlatformContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PREFS_NAME = "cafey_preferences"
private const val KEY_DARK_THEME = "dark_theme"

actual fun createThemePreferenceStorage(): ThemePreferenceStorage = AndroidThemePreferenceStorage()

/** Preferência simples (não sensível) — `SharedPreferences` comum, sem a criptografia usada em
 * `AndroidTokenStorage` para os tokens. */
class AndroidThemePreferenceStorage : ThemePreferenceStorage {
    private fun prefs() = AndroidPlatformContext.require()
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun save(darkTheme: Boolean) = withContext(Dispatchers.IO) {
        prefs().edit().putBoolean(KEY_DARK_THEME, darkTheme).apply()
    }

    override suspend fun load(): Boolean? = withContext(Dispatchers.IO) {
        val p = prefs()
        if (p.contains(KEY_DARK_THEME)) p.getBoolean(KEY_DARK_THEME, false) else null
    }
}
