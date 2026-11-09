package br.com.tavaressan.cafey.shared.network

import br.com.tavaressan.cafey.shared.ble.ComandoRemoto
import br.com.tavaressan.cafey.shared.domain.model.AcaoComando
import br.com.tavaressan.cafey.shared.domain.model.ComandoRequest
import br.com.tavaressan.cafey.shared.domain.model.ComandoResponse
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.path

/**
 * `POST /dispositivos/{id}/comando` — UC-06 (ligar), UC-07 (desligar) e UC-09 (cancelar).
 *
 * O backend responde 202: publicou o comando no tópico MQTT, não esperou a base executar. O novo
 * estado chega pela leitura periódica de [DeviceApi], não por esta chamada.
 */
class CommandApi(private val apiClient: ApiClient) : ComandoRemoto {

    override suspend fun ligar(dispositivoId: String): ComandoResponse =
        enviar(dispositivoId, ComandoRequest(AcaoComando.LIGAR))

    override suspend fun desligar(dispositivoId: String): ComandoResponse =
        enviar(dispositivoId, ComandoRequest(AcaoComando.DESLIGAR))

    override suspend fun cancelar(dispositivoId: String): ComandoResponse =
        enviar(dispositivoId, ComandoRequest(AcaoComando.CANCELAR))

    suspend fun esquecerRedeWifi(dispositivoId: String, ssid: String): ComandoResponse =
        enviar(dispositivoId, ComandoRequest(AcaoComando.ESQUECER_REDE, ssid = ssid))

    suspend fun esquecerTodasRedesWifi(dispositivoId: String): ComandoResponse =
        enviar(dispositivoId, ComandoRequest(AcaoComando.ESQUECER_TODAS_REDES))

    private suspend fun enviar(dispositivoId: String, request: ComandoRequest): ComandoResponse =
        apiClient.http.apiRequest {
            method = HttpMethod.Post
            url { path("dispositivos", dispositivoId, "comando") }
            contentType(ContentType.Application.Json)
            setBody(request)
        }
}
