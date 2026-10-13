package br.com.tavaressan.cafey.shared.preferences

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.prefs.Preferences

private const val KEY_DARK_THEME = "dark_theme"

actual fun createThemePreferenceStorage(): ThemePreferenceStorage = DesktopThemePreferenceStorage()

/** `java.util.prefs.Preferences` — mesma estratégia de `DesktopTokenStorage`. */
class DesktopThemePreferenceStorage : ThemePreferenceStorage {
    private val node = Preferences.userNodeForPackage(DesktopThemePreferenceStorage::class.java)

    override suspend fun save(darkTheme: Boolean) = withContext(Dispatchers.IO) {
        node.putBoolean(KEY_DARK_THEME, darkTheme)
        node.flush()
    }

    override suspend fun load(): Boolean? = withContext(Dispatchers.IO) {
        if (node.get(KEY_DARK_THEME, null) != null) node.getBoolean(KEY_DARK_THEME, false) else null
    }
}
