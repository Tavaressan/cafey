package br.com.tavaressan.cafey.shared.auth

import kotlinx.browser.localStorage

private const val KEY_ACCESS = "cafey_access_token"
private const val KEY_REFRESH = "cafey_refresh_token"

actual fun createTokenStorage(): TokenStorage = BrowserTokenStorage()

/** `localStorage` do navegador — persiste entre sessões da aba, como pedido para o Web. */
class BrowserTokenStorage : TokenStorage {
    override suspend fun save(tokens: StoredTokens) {
        localStorage.setItem(KEY_ACCESS, tokens.accessToken)
        localStorage.setItem(KEY_REFRESH, tokens.refreshToken)
    }

    override suspend fun load(): StoredTokens? {
        val access = localStorage.getItem(KEY_ACCESS)
        val refresh = localStorage.getItem(KEY_REFRESH)
        return if (access != null && refresh != null) StoredTokens(access, refresh) else null
    }

    override suspend fun clear() {
        localStorage.removeItem(KEY_ACCESS)
        localStorage.removeItem(KEY_REFRESH)
    }
}
