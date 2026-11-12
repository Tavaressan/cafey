package br.com.tavaressan.cafey.notification.webpush

import br.com.tavaressan.cafey.notification.CanalPush
import br.com.tavaressan.cafey.notification.MensagemPush
import br.com.tavaressan.cafey.notification.NotificacaoConfig
import br.com.tavaressan.cafey.notification.NotificacaoProperties
import br.com.tavaressan.cafey.notification.PlataformaPush
import br.com.tavaressan.cafey.notification.RegistroPush
import br.com.tavaressan.cafey.notification.RegistroPushService
import br.com.tavaressan.cafey.notification.ResultadoEnvio
import tools.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration

@Component
class WebPushCanalPush(
    private val properties: NotificacaoProperties,
    @Qualifier(NotificacaoConfig.HTTP_CLIENT) private val httpClient: HttpClient,
    private val mapper: ObjectMapper,
    private val registroPushService: RegistroPushService
) : CanalPush {

    private val logger = LoggerFactory.getLogger(WebPushCanalPush::class.java)

    override val plataforma = PlataformaPush.WEB

    private val vapid: WebPushVapid? = inicializarVapid()

    override val habilitado: Boolean = vapid != null

    init {
        if (!habilitado) {
            logger.warn(
                "Canal Web Push desligado: chaves VAPID ausentes, inválidas ou inconsistentes. " +
                "Configure CAFEY_PUSH_VAPID_PUBLIC_KEY, CAFEY_PUSH_VAPID_PRIVATE_KEY e CAFEY_PUSH_VAPID_SUBJECT para ativar envios web."
            )
        }
    }

    private fun inicializarVapid(): WebPushVapid? {
        val pub = properties.vapidPublicKey.trim()
        val priv = properties.vapidPrivateKey.trim()
        val subject = properties.vapidSubject.trim()
        if (pub.isEmpty() || priv.isEmpty() || subject.isEmpty()) return null
        val par = WebPushChaves.parVapid(pub, priv) ?: return null
        return WebPushVapid(par, subject)
    }

    override fun enviar(registro: RegistroPush, mensagem: MensagemPush): ResultadoEnvio {
        val v = vapid ?: return ResultadoEnvio.FALHA_PERMANENTE
        return try {
            if (!registroPushService.endpointPermitido(registro.destino)) {
                return ResultadoEnvio.FALHA_PERMANENTE
            }
            val uaPublic = decodificarChave(registro.chaveP256dh) ?: return ResultadoEnvio.FALHA_PERMANENTE
            val auth = decodificarChave(registro.chaveAuth) ?: return ResultadoEnvio.FALHA_PERMANENTE
            val payload = payloadJson(mensagem)
            val corpo = WebPushCriptografia.criptografar(
                WebPushCriptografia.EntradaCriptografia(uaPublic = uaPublic, authSecret = auth, plaintext = payload)
            )
            val request = HttpRequest.newBuilder()
                .uri(URI.create(registro.destino))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Encoding", "aes128gcm")
                .header("Content-Type", "application/octet-stream")
                .header("TTL", mensagem.tipo.ttl.seconds.toString())
                .header("Urgency", if (mensagem.tipo.urgente) "high" else "normal")
                .header("Topic", topic(mensagem.tag))
                .header("Authorization", v.autorizacao(registro.destino))
                .POST(HttpRequest.BodyPublishers.ofByteArray(corpo))
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.discarding())
            mapear(response.statusCode(), registro)
        } catch (_: IOException) {
            ResultadoEnvio.FALHA_TRANSITORIA
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            ResultadoEnvio.FALHA_TRANSITORIA
        } catch (e: Exception) {
            logger.warn("Erro inesperado ao enviar Web Push para destino {}: {}", registro.id, e.javaClass.simpleName)
            ResultadoEnvio.FALHA_TRANSITORIA
        }
    }

    internal fun payloadJson(mensagem: MensagemPush): ByteArray {
        val limite = 3900
        var corpo = mensagem.corpo
        while (true) {
            val dados = LinkedHashMap(mensagem.dados())
            dados["corpo"] = corpo
            val bytes = mapper.writeValueAsBytes(dados)
            if (bytes.size <= limite || corpo.isEmpty()) return bytes
            corpo = corpo.take((corpo.length * 3) / 4).trimEnd()
        }
    }

    internal fun topic(tag: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(tag.toByteArray(StandardCharsets.UTF_8))
        return WebPushChaves.codificarBase64Url(hash.copyOfRange(0, 24))
    }

    private fun decodificarChave(valor: String?): ByteArray? = try {
        if (valor.isNullOrBlank()) null else WebPushChaves.decodificarBase64Url(valor)
    } catch (_: Exception) { null }

    private fun mapear(status: Int, registro: RegistroPush): ResultadoEnvio = when {
        status == 200 || status == 201 || status == 202 -> ResultadoEnvio.ENTREGUE
        status == 404 || status == 410 -> ResultadoEnvio.DESTINO_INVALIDO
        status == 400 || status == 403 || status == 413 -> ResultadoEnvio.FALHA_PERMANENTE
        status == 429 || status in 500..599 -> ResultadoEnvio.FALHA_TRANSITORIA
        else -> {
            logger.warn("Web Push retornou {} inesperado para destino {}", status, registro.id)
            ResultadoEnvio.FALHA_PERMANENTE
        }
    }
}
