package br.com.tavaressan.cafey.shared.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.prefs.Preferences

private const val KEY_ACCESS = "access_token"
private const val KEY_REFRESH = "refresh_token"

actual fun createTokenStorage(): TokenStorage = DesktopTokenStorage()

/**
 * `java.util.prefs.Preferences` grava no registro do usuário no Windows (`HKEY_CURRENT_USER`) ou
 * em `~/.java/.userPrefs` no Linux/macOS — não é criptografado, mas fica fora do alcance de outras
 * contas do sistema operacional, o que é o "algo apropriado" pedido para o Desktop nesta fase.
 */
class DesktopTokenStorage : TokenStorage {
    private val node = Preferences.userNodeForPackage(DesktopTokenStorage::class.java)

    override suspend fun save(tokens: StoredTokens) = withContext(Dispatchers.IO) {
        node.put(KEY_ACCESS, tokens.accessToken)
        node.put(KEY_REFRESH, tokens.refreshToken)
        node.flush()
    }

    override suspend fun load(): StoredTokens? = withContext(Dispatchers.IO) {
        val access = node.get(KEY_ACCESS, null)
        val refresh = node.get(KEY_REFRESH, null)
        if (access != null && refresh != null) StoredTokens(access, refresh) else null
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        node.remove(KEY_ACCESS)
        node.remove(KEY_REFRESH)
        node.flush()
    }
}
