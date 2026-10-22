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
import br.com.tavaressan.cafey.shared.preferences.ThemePreference
import br.com.tavaressan.cafey.shared.preferences.createThemePreferenceStorage

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
