package br.com.tavaressan.cafey.shared.auth

/** Dublê em memória de [TokenStorage] para testes — sem disco, sem navegador. */
class FakeTokenStorage(initial: StoredTokens? = null) : TokenStorage {
    private var tokens: StoredTokens? = initial

    override suspend fun save(tokens: StoredTokens) {
        this.tokens = tokens
    }

    override suspend fun load(): StoredTokens? = tokens

    override suspend fun clear() {
        tokens = null
    }
}
