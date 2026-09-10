package br.com.tavaressan.cafey.shared.auth

/** Par de tokens persistido localmente após login/registro/refresh. */
data class StoredTokens(
    val accessToken: String,
    val refreshToken: String,
)

/**
 * Armazenamento seguro do par de tokens. A interface mora em `commonMain` (testável com um fake
 * em `commonTest`, sem tocar disco/navegador); a implementação concreta é escolhida por
 * plataforma via [createTokenStorage] (`expect`/`actual`): EncryptedSharedPreferences no Android,
 * `java.util.prefs.Preferences` no Desktop, `localStorage` do navegador no Web. Nenhuma delas mora
 * na UI — é a camada `shared` quem decide onde e como persistir (spec §2.3).
 */
interface TokenStorage {
    suspend fun save(tokens: StoredTokens)
    suspend fun load(): StoredTokens?
    suspend fun clear()
}

/** Fábrica da implementação de [TokenStorage] apropriada para a plataforma corrente. */
expect fun createTokenStorage(): TokenStorage
