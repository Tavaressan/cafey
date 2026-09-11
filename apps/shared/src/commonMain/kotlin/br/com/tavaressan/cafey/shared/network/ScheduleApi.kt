package br.com.tavaressan.cafey.shared.network

import br.com.tavaressan.cafey.shared.domain.model.AgendamentoResponse
import br.com.tavaressan.cafey.shared.domain.model.AtualizarAgendamentoRequest
import br.com.tavaressan.cafey.shared.domain.model.CriarAgendamentoRequest
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.path

/** `/dispositivos/{id}/agendamentos` — espelha `AgendamentoController`. */
class ScheduleApi(private val apiClient: ApiClient) {

    suspend fun listar(dispositivoId: String): List<AgendamentoResponse> = apiClient.http.apiRequest {
        method = HttpMethod.Get
        url { path("dispositivos", dispositivoId, "agendamentos") }
    }

    suspend fun criar(dispositivoId: String, request: CriarAgendamentoRequest): AgendamentoResponse =
        apiClient.http.apiRequest {
            method = HttpMethod.Post
            url { path("dispositivos", dispositivoId, "agendamentos") }
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    suspend fun atualizar(
        dispositivoId: String,
        agendamentoId: String,
        request: AtualizarAgendamentoRequest,
    ): AgendamentoResponse = apiClient.http.apiRequest {
        method = HttpMethod.Put
        url { path("dispositivos", dispositivoId, "agendamentos", agendamentoId) }
        contentType(ContentType.Application.Json)
        setBody(request)
    }

    suspend fun excluir(dispositivoId: String, agendamentoId: String) {
        apiClient.http.apiRequest<Unit> {
            method = HttpMethod.Delete
            url { path("dispositivos", dispositivoId, "agendamentos", agendamentoId) }
        }
    }
}
