package br.com.tavaressan.cafey.shared.ui.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.tavaressan.cafey.shared.domain.model.DispositivoResponse
import br.com.tavaressan.cafey.shared.network.ApiError
import br.com.tavaressan.cafey.shared.network.DeviceApi
import br.com.tavaressan.cafey.shared.preferences.ThemePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BaseUiState(
    val loading: Boolean = true,
    val device: DispositivoResponse? = null,
    val darkTheme: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Issue #182 — tela "Base" (dispositivo + alternador de tema). Espelha
 * `docs/docs_interface/prototype/base.html` só no que tem dado real via [DeviceApi] (nome, estado
 * online/offline): Wi-Fi, nuvem e firmware/OTA do protótipo não têm campo em
 * `DispositivoResponse`/endpoint no backend hoje — omitidos em vez de inventados, mesmo critério já
 * usado em `CareScreen`/`ScheduleScreen` para dados sem endpoint correspondente.
 */
class BaseViewModel(
    private val deviceApi: DeviceApi,
    private val themePreference: ThemePreference,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BaseUiState())
    val uiState: StateFlow<BaseUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            themePreference.darkTheme.collect { dark -> _uiState.update { it.copy(darkTheme = dark) } }
        }
        viewModelScope.launch {
            try {
                // MVP de um único dispositivo, mesma simplificação de HomeViewModel (APP-04).
                val device = deviceApi.listar().firstOrNull()
                _uiState.update { it.copy(loading = false, device = device) }
            } catch (e: ApiError) {
                _uiState.update { it.copy(loading = false, errorMessage = e.message) }
            }
        }
    }

    fun toggleTheme() {
        viewModelScope.launch { themePreference.toggle() }
    }
}
