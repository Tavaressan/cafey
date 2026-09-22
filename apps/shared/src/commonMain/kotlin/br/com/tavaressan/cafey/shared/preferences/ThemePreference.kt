package br.com.tavaressan.cafey.shared.preferences

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado reativo do alternador claro/escuro (issue #182) — mora fora de qualquer `ViewModel` de
 * tela porque precisa ser lido acima do `NavHost` (`App.kt`, para envolver tudo em `CafeyTheme`) e
 * alterado de dentro dele (`BaseScreen`). Uma instância por [br.com.tavaressan.cafey.shared.AppContainer],
 * como os demais clientes de rede.
 */
class ThemePreference(private val storage: ThemePreferenceStorage) {
    private val _darkTheme = MutableStateFlow(false)
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

    /** Carrega a preferência persistida — chamado uma vez, no topo da árvore de composição. */
    suspend fun load() {
        _darkTheme.value = storage.load() ?: false
    }

    suspend fun toggle() {
        val novo = !_darkTheme.value
        _darkTheme.value = novo
        storage.save(novo)
    }
}
