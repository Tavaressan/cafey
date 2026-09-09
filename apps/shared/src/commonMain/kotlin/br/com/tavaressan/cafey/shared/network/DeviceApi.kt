package br.com.tavaressan.cafey.shared.network

import br.com.tavaressan.cafey.shared.domain.model.AtualizarDispositivoRequest
import br.com.tavaressan.cafey.shared.domain.model.CompartilhamentoResponse
import br.com.tavaressan.cafey.shared.domain.model.CompartilharDispositivoRequest
import br.com.tavaressan.cafey.shared.domain.model.CriarDispositivoRequest
import br.com.tavaressan.cafey.shared.domain.model.DispositivoResponse
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.path

/** `/dispositivos/*` — espelha `br.com.tavaressan.cafey.device.DispositivoController`. */
class DeviceApi(private val apiClient: ApiClient) {

    suspend fun listar(): List<DispositivoResponse> = apiClient.http.apiRequest {
        method = HttpMethod.Get
        url { path("dispositivos") }
    }

    suspend fun obter(id: String): DispositivoResponse = apiClient.http.apiRequest {
        method = HttpMethod.Get
        url { path("dispositivos", id) }
    }

    suspend fun criar(request: CriarDispositivoRequest): DispositivoResponse = apiClient.http.apiRequest {
        method = HttpMethod.Post
        url { path("dispositivos") }
        contentType(ContentType.Application.Json)
        setBody(request)
    }

    suspend fun atualizar(id: String, request: AtualizarDispositivoRequest): DispositivoResponse =
        apiClient.http.apiRequest {
            method = HttpMethod.Put
            url { path("dispositivos", id) }
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    suspend fun excluir(id: String) {
        apiClient.http.apiRequest<Unit> {
            method = HttpMethod.Delete
            url { path("dispositivos", id) }
        }
    }

    suspend fun compartilhar(id: String, request: CompartilharDispositivoRequest): CompartilhamentoResponse =
        apiClient.http.apiRequest {
            method = HttpMethod.Post
            url { path("dispositivos", id, "compartilhar") }
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    suspend fun listarCompartilhamentos(id: String): List<CompartilhamentoResponse> = apiClient.http.apiRequest {
        method = HttpMethod.Get
        url { path("dispositivos", id, "compartilhamentos") }
    }
}
