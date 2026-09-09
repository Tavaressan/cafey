package br.com.tavaressan.cafey.shared.network

import br.com.tavaressan.cafey.shared.domain.model.AcaoComando
import br.com.tavaressan.cafey.shared.domain.model.ComandoRequest
import br.com.tavaressan.cafey.shared.domain.model.DispositivoResponse
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.path

/**
 * `POST /dispositivos/{id}/comando` — UC-06 (ligar), UC-07 (desligar) e UC-09 (cancelar).
 *
 * **Ver o aviso em [ComandoRequest]:** este endpoint ainda não existe no backend hoje. A classe
 * está pronta para o dia em que existir; até lá, chamá-la resulta em `ApiError.Http(404, ...)`.
 */
class CommandApi(private val apiClient: ApiClient) {

    suspend fun ligar(dispositivoId: String): DispositivoResponse =
        enviar(dispositivoId, AcaoComando.LIGAR)

    suspend fun desligar(dispositivoId: String): DispositivoResponse =
        enviar(dispositivoId, AcaoComando.DESLIGAR)

    suspend fun cancelar(dispositivoId: String): DispositivoResponse =
        enviar(dispositivoId, AcaoComando.CANCELAR)

    private suspend fun enviar(dispositivoId: String, acao: AcaoComando): DispositivoResponse =
        apiClient.http.apiRequest {
            method = HttpMethod.Post
            url { path("dispositivos", dispositivoId, "comando") }
            contentType(ContentType.Application.Json)
            setBody(ComandoRequest(acao))
        }
}
