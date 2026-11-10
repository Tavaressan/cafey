package br.com.tavaressan.cafey.shared.preferences

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado reativo do onboarding de primeira utilização (issue #312). Vive no [br.com.tavaressan.cafey.shared.AppContainer]
 * para que o gate de navegação (exibição automática na primeira abertura) e a ação explícita
 * "Ver tutorial" compartilhem a mesma preferência persistida.
 */
class OnboardingPreference(private val storage: OnboardingPreferenceStorage) {
    private val _completed = MutableStateFlow(false)
    val completed: StateFlow<Boolean> = _completed.asStateFlow()

    /** Carrega a preferência persistida — chamado no gate de navegação. */
    suspend fun load() {
        _completed.value = storage.loadCompleted() ?: false
    }

    /** Marca como concluído/pulado para não exibir automaticamente de novo. */
    suspend fun markCompleted() {
        _completed.value = true
        storage.saveCompleted(true)
    }
}
