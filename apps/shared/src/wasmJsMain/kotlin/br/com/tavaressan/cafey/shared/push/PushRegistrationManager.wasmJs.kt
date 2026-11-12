@file:OptIn(ExperimentalWasmJsInterop::class)

package br.com.tavaressan.cafey.shared.push

import br.com.tavaressan.cafey.shared.network.ApiClient
import br.com.tavaressan.cafey.shared.network.ApiError
import br.com.tavaressan.cafey.shared.network.PushApi
import kotlinx.browser.localStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.js.ExperimentalWasmJsInterop

@Serializable
private data class WebSubJson(val endpoint: String = "", val p256dh: String = "", val auth: String = "")

private val json = Json { ignoreUnknownKeys = true }

private class WasmPushRegistrationManager(
    private val apiClient: ApiClient,
    private val pushApi: PushApi,
) : PushRegistrationManager {

    override suspend fun registrarSePossivel() {
        if (!apiClient.isAuthenticated()) return
        if (!webPushIsSupported()) return
        if (webPushPermission() != "granted") return
        try {
            val subStr = webPushGetSubscriptionJson().awaitWasm().toString()
            if (subStr.isBlank()) return
            val sub = json.decodeFromString<WebSubJson>(subStr)
            if (sub.endpoint.isBlank() || sub.p256dh.isBlank() || sub.auth.isBlank()) return
            pushApi.registrarWeb(sub.endpoint, sub.p256dh, sub.auth)
            localStorage.setItem(KEY_LAST_ENDPOINT, sub.endpoint)
            println("[push-web] Destino de push Web registrado.")
        } catch (e: Exception) {
            println("[push-web] Falha ao registrar destino de push Web (não crítico): ${e.message}")
        }
    }

    override suspend fun removerAntesDeLogout() {
        if (!apiClient.isAuthenticated()) return
        try {
            val endpoint = if (webPushIsSupported()) {
                val ep = webPushUnsubscribe().awaitWasm().toString()
                ep.ifBlank { localStorage.getItem(KEY_LAST_ENDPOINT).orEmpty() }
            } else {
                localStorage.getItem(KEY_LAST_ENDPOINT).orEmpty()
            }
            if (endpoint.isNotBlank()) {
                try {
                    pushApi.remover(endpoint)
                } catch (e: Exception) {
                    println("[push-web] Falha ao remover destino no backend (não crítico): ${e.message}")
                }
                localStorage.removeItem(KEY_LAST_ENDPOINT)
            }
        } catch (e: Exception) {
            println("[push-web] Falha ao remover destino de push Web (não crítico): ${e.message}")
        }
    }

    companion object {
        const val KEY_LAST_ENDPOINT = "cafey_push_web_endpoint"
    }
}

actual fun createPushRegistrationManager(apiClient: ApiClient, pushApi: PushApi): PushRegistrationManager =
    WasmPushRegistrationManager(apiClient, pushApi)

/** API de suporte para a UI Web (permissão/estado/ativação por gesto do usuário). */
object WebPushUiSupport {
    fun isSupported(): Boolean = try { webPushIsSupported() } catch (_: Throwable) { false }

    fun permission(): String = try { webPushPermission() } catch (_: Throwable) { "unsupported" }

    suspend fun registerServiceWorker(): Result<Unit> = runCatching {
        val res = webPushRegisterSw().awaitWasm().toString()
        if (res.startsWith("err:")) error(res.removePrefix("err:"))
    }

    suspend fun requestPermissionAndSubscribe(pushApi: PushApi): Result<Unit> = runCatching {
        if (!isSupported()) error("não suportado")
        registerServiceWorker().getOrThrow()
        val perm = webPushRequestPermission().awaitWasm().toString()
        if (perm != "granted") error("permission:$perm")
        val vapid = try {
            pushApi.obterChaveVapid()
        } catch (e: ApiError.Http) {
            if (e.status == 503) error("vapid_indisponivel") else throw e
        }
        val subStr = webPushSubscribe(vapid.publicKey).awaitWasm().toString()
        val sub = json.decodeFromString<WebSubJson>(subStr)
        if (sub.endpoint.isBlank() || sub.p256dh.isBlank() || sub.auth.isBlank()) error("assinatura inválida")
        pushApi.registrarWeb(sub.endpoint, sub.p256dh, sub.auth)
        localStorage.setItem(WasmPushRegistrationManager.KEY_LAST_ENDPOINT, sub.endpoint)
    }
}
