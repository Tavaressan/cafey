package br.com.tavaressan.cafey.shared.network

import br.com.tavaressan.cafey.shared.domain.model.EstatisticasConsumoResponse
import br.com.tavaressan.cafey.shared.domain.model.EventoResponse
import br.com.tavaressan.cafey.shared.domain.model.PageResponse
import br.com.tavaressan.cafey.shared.domain.model.StatusDescalcificacaoResponse
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import io.ktor.http.path

/** `/dispositivos/{id}/eventos|estatisticas|descalcificacao` — espelha `EventoController`. */
class EventApi(private val apiClient: ApiClient) {

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
}
