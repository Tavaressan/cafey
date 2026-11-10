package br.com.tavaressan.cafey.shared.preferences

import kotlinx.browser.localStorage

private const val KEY_ONBOARDING_COMPLETED = "cafey_onboarding_completed"

actual fun createOnboardingPreferenceStorage(): OnboardingPreferenceStorage =
    BrowserOnboardingPreferenceStorage()

/** `localStorage` do navegador — mesma estratégia de [BrowserThemePreferenceStorage]. */
class BrowserOnboardingPreferenceStorage : OnboardingPreferenceStorage {
    override suspend fun saveCompleted(completed: Boolean) {
        localStorage.setItem(KEY_ONBOARDING_COMPLETED, completed.toString())
    }

    override suspend fun loadCompleted(): Boolean? =
        localStorage.getItem(KEY_ONBOARDING_COMPLETED)?.toBooleanStrictOrNull()
}
