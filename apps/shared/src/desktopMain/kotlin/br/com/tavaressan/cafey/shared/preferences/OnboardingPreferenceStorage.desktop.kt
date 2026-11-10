package br.com.tavaressan.cafey.shared.preferences

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.prefs.Preferences

private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"

actual fun createOnboardingPreferenceStorage(): OnboardingPreferenceStorage =
    DesktopOnboardingPreferenceStorage()

/** `java.util.prefs.Preferences` — mesma estratégia de [DesktopThemePreferenceStorage]. */
class DesktopOnboardingPreferenceStorage : OnboardingPreferenceStorage {
    private val node = Preferences.userNodeForPackage(DesktopOnboardingPreferenceStorage::class.java)

    override suspend fun saveCompleted(completed: Boolean) = withContext(Dispatchers.IO) {
        node.putBoolean(KEY_ONBOARDING_COMPLETED, completed)
        node.flush()
    }

    override suspend fun loadCompleted(): Boolean? = withContext(Dispatchers.IO) {
        if (node.get(KEY_ONBOARDING_COMPLETED, null) != null) node.getBoolean(KEY_ONBOARDING_COMPLETED, false) else null
    }
}
