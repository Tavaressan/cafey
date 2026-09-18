package br.com.tavaressan.cafey.shared.preferences

/**
 * Persistência da escolha de tema claro/escuro (`CafeyTheme.kt:31` — alternador manual na tela
 * "Base", issue #182). Mesma estratégia de `TokenStorage` (`shared/auth/TokenStorage.kt`): a
 * interface mora em `commonMain` (testável com um fake, sem tocar disco/navegador), e a
 * implementação concreta é escolhida por plataforma via [createThemePreferenceStorage]
 * (`expect`/`actual`).
 */
interface ThemePreferenceStorage {
    suspend fun save(darkTheme: Boolean)

    /** `null` quando nunca foi salvo — o caller decide o padrão (claro). */
    suspend fun load(): Boolean?
}

/** Fábrica da implementação de [ThemePreferenceStorage] apropriada para a plataforma corrente. */
expect fun createThemePreferenceStorage(): ThemePreferenceStorage
