package br.com.tavaressan.cafey.shared.push

import br.com.tavaressan.cafey.shared.network.ApiClient
import br.com.tavaressan.cafey.shared.network.PushApi

private object NoopPushRegistrationManager : PushRegistrationManager {
    override suspend fun registrarSePossivel() = Unit
    override suspend fun removerAntesDeLogout() = Unit
}

actual fun createPushRegistrationManager(apiClient: ApiClient, pushApi: PushApi): PushRegistrationManager =
    NoopPushRegistrationManager
