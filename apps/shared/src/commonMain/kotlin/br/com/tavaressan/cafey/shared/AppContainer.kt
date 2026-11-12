package br.com.tavaressan.cafey.shared

import br.com.tavaressan.cafey.shared.auth.TokenStorage
import br.com.tavaressan.cafey.shared.auth.createTokenStorage
import br.com.tavaressan.cafey.shared.ble.BleClient
import br.com.tavaressan.cafey.shared.ble.createPlatformBleClient
import br.com.tavaressan.cafey.shared.network.ApiClient
import br.com.tavaressan.cafey.shared.network.AuthApi
import br.com.tavaressan.cafey.shared.network.CommandApi
import br.com.tavaressan.cafey.shared.network.DeviceApi
import br.com.tavaressan.cafey.shared.network.EventApi
import br.com.tavaressan.cafey.shared.network.ScheduleApi
import br.com.tavaressan.cafey.shared.network.createDemoHttpClient
import br.com.tavaressan.cafey.shared.network.platformDefaultBaseUrl
import br.com.tavaressan.cafey.shared.notifications.DesktopNotificationDetector
import br.com.tavaressan.cafey.shared.notifications.DesktopNotificationEvent
import br.com.tavaressan.cafey.shared.preferences.OnboardingPreference
import br.com.tavaressan.cafey.shared.preferences.createOnboardingPreferenceStorage
import br.com.tavaressan.cafey.shared.preferences.ThemePreference
import br.com.tavaressan.cafey.shared.preferences.createThemePreferenceStorage
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Composição manual de dependências da camada `shared` — sem framework de DI, é pequeno o
 * suficiente para não justificar um (YAGNI). Cada `*App` cria uma instância só, no topo
 * (`MainActivity`, `main()` do desktop/web), e repassa para as telas via `CompositionLocal` ou
 * parâmetro direto.
 */
class AppContainer(
    val baseUrl: String = platformDefaultBaseUrl,
    val isDemoMode: Boolean = false,
    customApiClient: ApiClient? = null,
    customTokenStorage: TokenStorage? = null,
    customBleClient: BleClient? = null,
) {
    val tokenStorage: TokenStorage = customTokenStorage ?: createTokenStorage()
    val apiClient: ApiClient = customApiClient ?: if (isDemoMode) {
        ApiClient(
            baseUrl = baseUrl,
            tokenStorage = tokenStorage,
            httpClient = createDemoHttpClient(baseUrl, tokenStorage),
        )
    } else {
        ApiClient(baseUrl, tokenStorage)
    }

    val authApi: AuthApi = AuthApi(apiClient, tokenStorage)
    val deviceApi: DeviceApi = DeviceApi(apiClient)
    val scheduleApi: ScheduleApi = ScheduleApi(apiClient)
    val eventApi: EventApi = EventApi(apiClient)
    val commandApi: CommandApi = CommandApi(apiClient)

    /** Cliente BLE opcional (injetado por plataforma, ex.: Android; nulo em Desktop/Web). */
    val bleClient: BleClient? = customBleClient ?: createPlatformBleClient()

    // Issue #182 — alternador claro/escuro da tela "Base"; precisa viver aqui (não num ViewModel de
    // tela) porque é lido acima do NavHost (App.kt, para CafeyTheme) e alterado de dentro dele.
    val themePreference: ThemePreference = ThemePreference(createThemePreferenceStorage())

    // Issue #312 — onboarding interativo de primeira utilização (gate automático + "Ver tutorial").
    val onboardingPreference: OnboardingPreference = OnboardingPreference(createOnboardingPreferenceStorage())

    // Issue #282 — Suporte a múltiplos dispositivos com seletor no cabeçalho/navegação
    val activeDeviceId: MutableStateFlow<String?> = MutableStateFlow(null)

    // #366 — notificações locais do desktop alimentadas pelo polling do HomeViewModel
    val desktopNotificationDetector: DesktopNotificationDetector = DesktopNotificationDetector()
    private val _desktopNotifications = MutableSharedFlow<DesktopNotificationEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val desktopNotifications: SharedFlow<DesktopNotificationEvent> = _desktopNotifications.asSharedFlow()

    fun emitDesktopNotification(event: DesktopNotificationEvent) {
        _desktopNotifications.tryEmit(event)
    }

    fun selectDevice(id: String?) {
        activeDeviceId.value = id
    }

    companion object {
        fun demo(
            baseUrl: String = "https://demo.cafey.local",
            tokenStorage: TokenStorage? = null,
            bleClient: BleClient? = null,
        ): AppContainer =
            AppContainer(
                baseUrl = baseUrl,
                isDemoMode = true,
                customTokenStorage = tokenStorage,
                customBleClient = bleClient,
            )
    }
}
