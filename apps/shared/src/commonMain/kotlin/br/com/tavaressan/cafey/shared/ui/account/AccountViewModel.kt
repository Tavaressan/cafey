package br.com.tavaressan.cafey.shared.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.tavaressan.cafey.shared.network.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountUiState(val loggedOut: Boolean = false)

/**
 * Issue #183 — `ApiClient.kt:93` já tinha `logout()` pronto, sem nenhum call site. Mesmo padrão de
 * `LoginViewModel`/`LoginScreen`: o `ViewModel` só marca `loggedOut = true` no estado; quem navega
 * (efeito colateral) é a tela, observando via `LaunchedEffect`.
 */
class AccountViewModel(private val apiClient: ApiClient) : ViewModel() {
    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    fun logout() {
        viewModelScope.launch {
            apiClient.logout()
            _uiState.update { it.copy(loggedOut = true) }
        }
    }
}
