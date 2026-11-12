package br.com.tavaressan.cafey.shared.network

import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.path
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegistroPushAndroidRequest(
    val plataforma: String = "ANDROID",
    val token: String,
)

@Serializable
data class RegistroPushWebRequest(
    val plataforma: String = "WEB",
    val endpoint: String,
    val p256dh: String,
    val auth: String,
)

@Serializable
data class RegistroPushResponse(
    val id: String,
    val plataforma: String,
    val criadoEm: String? = null,
    val ultimoUsoEm: String? = null,
)

@Serializable
data class RemoverRegistroPushRequest(
    val destino: String,
)

@Serializable
data class VapidPublicKeyResponse(
    val publicKey: String,
)

/** Endpoints em `/notificacoes` do backend (épico #318). */
class PushApi(private val apiClient: ApiClient) {

    suspend fun registrarAndroid(tokenFcm: String): RegistroPushResponse =
        apiClient.http.apiRequest {
            method = HttpMethod.Post
            url { path("notificacoes", "registros") }
            contentType(ContentType.Application.Json)
            setBody(RegistroPushAndroidRequest(token = tokenFcm))
        }

    suspend fun registrarWeb(endpoint: String, p256dh: String, auth: String): RegistroPushResponse =
        apiClient.http.apiRequest {
            method = HttpMethod.Post
            url { path("notificacoes", "registros") }
            contentType(ContentType.Application.Json)
            setBody(RegistroPushWebRequest(endpoint = endpoint, p256dh = p256dh, auth = auth))
        }

    suspend fun remover(destino: String) {
        apiClient.http.apiRequest<Unit> {
            method = HttpMethod.Post
            url { path("notificacoes", "registros", "remover") }
            contentType(ContentType.Application.Json)
            setBody(RemoverRegistroPushRequest(destino = destino))
        }
    }

    suspend fun obterChaveVapid(): VapidPublicKeyResponse =
        apiClient.http.apiRequest {
            method = HttpMethod.Get
            url { path("notificacoes", "vapid-public-key") }
        }
}
