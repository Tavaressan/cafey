package br.com.tavaressan.cafey.shared.push

import br.com.tavaressan.cafey.shared.network.ApiClient
import br.com.tavaressan.cafey.shared.network.PushApi

/**
 * Ponto único de registro/remoção do destino de push da plataforma atual.
 *
 * - [registrarSePossivel]: chamado após login bem-sucedido e na abertura com sessão ativa.
 * - [removerAntesDeLogout]: chamado no logout **antes** de apagar o token JWT.
 *
 * Implementações de plataforma sem push (desktop) são no-op. Falhas de rede não devem propagar.
 */
interface PushRegistrationManager {
    suspend fun registrarSePossivel()
    suspend fun removerAntesDeLogout()
}

expect fun createPushRegistrationManager(apiClient: ApiClient, pushApi: PushApi): PushRegistrationManager
