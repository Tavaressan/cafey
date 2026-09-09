package br.com.tavaressan.cafey.shared

import br.com.tavaressan.cafey.shared.auth.TokenStorage
import br.com.tavaressan.cafey.shared.auth.createTokenStorage
import br.com.tavaressan.cafey.shared.network.ApiClient
import br.com.tavaressan.cafey.shared.network.AuthApi
import br.com.tavaressan.cafey.shared.network.CommandApi
import br.com.tavaressan.cafey.shared.network.DeviceApi
import br.com.tavaressan.cafey.shared.network.EventApi
import br.com.tavaressan.cafey.shared.network.ScheduleApi
import br.com.tavaressan.cafey.shared.network.platformDefaultBaseUrl

/**
 * Composição manual de dependências da camada `shared` — sem framework de DI, é pequeno o
 * suficiente para não justificar um (YAGNI). Cada `*App` cria uma instância só, no topo
 * (`MainActivity`, `main()` do desktop/web), e repassa para as telas via `CompositionLocal` ou
 * parâmetro direto.
 */
class AppContainer(baseUrl: String = platformDefaultBaseUrl) {
    val tokenStorage: TokenStorage = createTokenStorage()
    val apiClient: ApiClient = ApiClient(baseUrl, tokenStorage)

    val authApi: AuthApi = AuthApi(apiClient, tokenStorage)
    val deviceApi: DeviceApi = DeviceApi(apiClient)
    val scheduleApi: ScheduleApi = ScheduleApi(apiClient)
    val eventApi: EventApi = EventApi(apiClient)
    val commandApi: CommandApi = CommandApi(apiClient)
}
