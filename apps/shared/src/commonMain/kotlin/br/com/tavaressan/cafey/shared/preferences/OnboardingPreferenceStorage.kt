package br.com.tavaressan.cafey.shared.preferences

/**
 * Persistência da conclusão do onboarding interativo de primeira utilização (issue #312).
 * "Concluir ou pular" contam como visto: o tutorial não aparece de novo, salvo ação explícita
 * ("Ver tutorial"). Mesma estratégia de [ThemePreferenceStorage]: interface em `commonMain`,
 * implementação concreta por plataforma via [createOnboardingPreferenceStorage] (expect/actual).
 */
interface OnboardingPreferenceStorage {
    suspend fun saveCompleted(completed: Boolean)

    /** `null` quando nunca foi salvo — tratado como não concluído. */
    suspend fun loadCompleted(): Boolean?
}

/** Fábrica da implementação de [OnboardingPreferenceStorage] apropriada para a plataforma corrente. */
expect fun createOnboardingPreferenceStorage(): OnboardingPreferenceStorage
