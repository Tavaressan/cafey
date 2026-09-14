package br.com.tavaressan.cafey.shared.network

import br.com.tavaressan.cafey.shared.ble.EventoProxyRemoto
import br.com.tavaressan.cafey.shared.domain.model.BleEvento
import br.com.tavaressan.cafey.shared.domain.model.EstatisticasConsumoResponse
import br.com.tavaressan.cafey.shared.domain.model.EventoResponse
import br.com.tavaressan.cafey.shared.domain.model.PageResponse
import br.com.tavaressan.cafey.shared.domain.model.ProxyBleEventosRequest
import br.com.tavaressan.cafey.shared.domain.model.StatusDescalcificacaoResponse
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.path

/** `/dispositivos/{id}/eventos|estatisticas|descalcificacao` — espelha `EventoController`. */
class EventApi(private val apiClient: ApiClient) : EventoProxyRemoto {

    suspend fun listarEventos(
        dispositivoId: String,
        page: Int = 0,
        size: Int = 20,
    ): PageResponse<EventoResponse> = apiClient.http.apiRequest {
        method = HttpMethod.Get
        url {
            path("dispositivos", dispositivoId, "eventos")
            parameters.append("page", page.toString())
            parameters.append("size", size.toString())
        }
    }

    suspend fun obterEstatisticas(dispositivoId: String): EstatisticasConsumoResponse = apiClient.http.apiRequest {
        method = HttpMethod.Get
        url { path("dispositivos", dispositivoId, "estatisticas") }
    }

    suspend fun obterStatusDescalcificacao(dispositivoId: String): StatusDescalcificacaoResponse =
        apiClient.http.apiRequest {
            method = HttpMethod.Get
            url { path("dispositivos", dispositivoId, "descalcificacao") }
        }

    suspend fun darBaixaDescalcificacao(dispositivoId: String): StatusDescalcificacaoResponse =
        apiClient.http.apiRequest {
            method = HttpMethod.Post
            url { path("dispositivos", dispositivoId, "descalcificacao", "baixa") }
        }

    /** Repassa ao backend, em nome do dispositivo, eventos lidos da fila local via BLE (spec §6.5,
     * passo 2) — usado por [br.com.tavaressan.cafey.shared.ble.BleEventProxy]. */
    override suspend fun enviarProxyBle(dispositivoId: String, eventos: List<BleEvento>): List<EventoResponse> =
        apiClient.http.apiRequest {
            method = HttpMethod.Post
            url { path("dispositivos", dispositivoId, "eventos", "proxy-ble") }
            contentType(ContentType.Application.Json)
            setBody(ProxyBleEventosRequest(eventos))
        }
}
