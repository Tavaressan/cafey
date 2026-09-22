package br.com.tavaressan.cafey.shared.preferences

import kotlinx.browser.localStorage

private const val KEY_DARK_THEME = "cafey_dark_theme"

actual fun createThemePreferenceStorage(): ThemePreferenceStorage = BrowserThemePreferenceStorage()

/** `localStorage` do navegador — mesma estratégia de `BrowserTokenStorage`. */
class BrowserThemePreferenceStorage : ThemePreferenceStorage {
    override suspend fun save(darkTheme: Boolean) {
        localStorage.setItem(KEY_DARK_THEME, darkTheme.toString())
    }

    override suspend fun load(): Boolean? = localStorage.getItem(KEY_DARK_THEME)?.toBooleanStrictOrNull()
}
