package br.com.tavaressan.cafey.shared.preferences

import android.content.Context
import br.com.tavaressan.cafey.shared.auth.AndroidPlatformContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PREFS_NAME = "cafey_preferences"
private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"

actual fun createOnboardingPreferenceStorage(): OnboardingPreferenceStorage =
    AndroidOnboardingPreferenceStorage()

/** Preferência simples (não sensível) — `SharedPreferences`, mesma base de [AndroidThemePreferenceStorage]. */
class AndroidOnboardingPreferenceStorage : OnboardingPreferenceStorage {
    private fun prefs() = AndroidPlatformContext.require()
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun saveCompleted(completed: Boolean) = withContext(Dispatchers.IO) {
        prefs().edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }

    override suspend fun loadCompleted(): Boolean? = withContext(Dispatchers.IO) {
        val p = prefs()
        if (p.contains(KEY_ONBOARDING_COMPLETED)) p.getBoolean(KEY_ONBOARDING_COMPLETED, false) else null
    }
}
