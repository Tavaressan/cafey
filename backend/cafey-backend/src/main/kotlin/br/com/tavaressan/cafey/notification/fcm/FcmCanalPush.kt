package br.com.tavaressan.cafey.notification.fcm

import br.com.tavaressan.cafey.notification.CanalPush
import br.com.tavaressan.cafey.notification.MensagemPush
import br.com.tavaressan.cafey.notification.NotificacaoProperties
import br.com.tavaressan.cafey.notification.PlataformaPush
import br.com.tavaressan.cafey.notification.RegistroPush
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
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration

/**
 * Canal de envio de push para Android via Firebase Cloud Messaging HTTP v1 (#362).
 *
 * Se [NotificacaoProperties.fcmCredenciaisPath] estiver vazio ou o arquivo for inválido, o canal
 * fica desligado ([habilitado] = false) e o boot segue normalmente — adequado para dev/teste sem
 * configuração de Firebase.
 *
 * Nunca lança exceção: todas as falhas são capturadas e devolvidas como [ResultadoEnvio].
 * Nenhum token FCM, access token ou chave privada aparece nos logs.
 */
@Component
class FcmCanalPush(
    private val properties: NotificacaoProperties,
    @Qualifier("notificacaoHttpClient") private val httpClient: HttpClient,
    private val mapper: ObjectMapper
) : CanalPush {

    private val logger = LoggerFactory.getLogger(FcmCanalPush::class.java)

    override val plataforma = PlataformaPush.ANDROID

    private data class Estado(val credenciais: FcmCredenciais, val tokenService: FcmTokenService)

    // Carregados uma única vez; null = canal desligado
    private val estado: Estado? = carregarCredenciais().let { (c, t) ->
        if (c != null && t != null) Estado(c, t) else null
    }

    override val habilitado: Boolean = estado != null

    init {
        if (!habilitado) {
            logger.warn(
                "Canal FCM desligado: fcmCredenciaisPath está vazio, inválido ou o arquivo não existe. " +
                "Configure CAFEY_PUSH_FCM_CREDENTIALS_PATH para ativar envios para Android."
            )
        }
    }

    override fun enviar(registro: RegistroPush, mensagem: MensagemPush): ResultadoEnvio {
        val est = estado ?: return ResultadoEnvio.FALHA_PERMANENTE
        val svc = est.tokenService
        val creds = est.credenciais

        return try {
            val token = try {
                svc.obterToken()
            } catch (e: Exception) {
                logger.warn("Erro ao obter access token FCM para destino {}: {}", registro.id, e.message)
                return ResultadoEnvio.FALHA_TRANSITORIA
            }
            enviarComToken(token, creds, registro, mensagem, svc)
        } catch (e: IOException) {
            logger.warn("IOException ao enviar push FCM para destino {}: {}", registro.id, e.message)
            ResultadoEnvio.FALHA_TRANSITORIA
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.warn("Envio FCM interrompido para destino {}", registro.id)
            ResultadoEnvio.FALHA_TRANSITORIA
        } catch (e: Exception) {
            logger.warn("Erro inesperado ao enviar push FCM para destino {}: {}", registro.id, e.message)
            ResultadoEnvio.FALHA_TRANSITORIA
        }
    }

    private fun enviarComToken(
        accessToken: String,
        creds: FcmCredenciais,
        registro: RegistroPush,
        mensagem: MensagemPush,
        svc: FcmTokenService
    ): ResultadoEnvio {
        val url = "${properties.fcmBaseUrl}/v1/projects/${creds.projectId}/messages:send"
        val corpo = construirCorpo(registro.destino, mensagem)
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json; charset=UTF-8")
            .POST(HttpRequest.BodyPublishers.ofString(corpo))
            .timeout(Duration.ofSeconds(10))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        return mapearResposta(response, registro, svc)
    }

    private fun construirCorpo(fcmToken: String, mensagem: MensagemPush): String {
        val tipo = mensagem.tipo
        val prioridade = if (tipo.urgente) "HIGH" else "NORMAL"
        val ttlSegundos = tipo.ttl.seconds

        val androidNotification = mapOf(
            "channel_id" to tipo.canalAndroid,
            "tag" to mensagem.tag
        )
        val android = mapOf(
            "priority" to prioridade,
            "ttl" to "${ttlSegundos}s",
            "notification" to androidNotification
        )
        val notification = mapOf(
            "title" to mensagem.titulo,
            "body" to mensagem.corpo
        )
        val message = mapOf(
            "token" to fcmToken,
            "notification" to notification,
            "data" to mensagem.dados(),
            "android" to android
        )
        return mapper.writeValueAsString(mapOf("message" to message))
    }

    @Suppress("ReturnCount")
    private fun mapearResposta(
        response: HttpResponse<String>,
        registro: RegistroPush,
        svc: FcmTokenService
    ): ResultadoEnvio {
        val status = response.statusCode()
        return when {
            status == 200 -> ResultadoEnvio.ENTREGUE

            status == 401 -> {
                svc.invalidar()
                logger.warn("FCM retornou 401 para destino {}; access token invalidado", registro.id)
                ResultadoEnvio.FALHA_TRANSITORIA
            }

            status == 404 -> {
                logger.info("FCM retornou 404 para destino {} (token não registrado)", registro.id)
                ResultadoEnvio.DESTINO_INVALIDO
            }

            status == 403 -> {
                if (contemErro(response.body(), "SENDER_ID_MISMATCH")) {
                    logger.warn("FCM retornou 403 SENDER_ID_MISMATCH para destino {}", registro.id)
                    ResultadoEnvio.DESTINO_INVALIDO
                } else {
                    logger.warn("FCM retornou 403 para destino {}: {}", registro.id, censurado(response.body()))
                    ResultadoEnvio.FALHA_PERMANENTE
                }
            }

            status == 400 -> {
                when {
                    contemErro(response.body(), "UNREGISTERED") -> {
                        logger.info("FCM retornou 400 UNREGISTERED para destino {}", registro.id)
                        ResultadoEnvio.DESTINO_INVALIDO
                    }
                    contemErro(response.body(), "INVALID_ARGUMENT") -> {
                        logger.warn("FCM retornou 400 INVALID_ARGUMENT para destino {}", registro.id)
                        ResultadoEnvio.FALHA_PERMANENTE
                    }
                    else -> {
                        logger.warn("FCM retornou 400 para destino {}: {}", registro.id, censurado(response.body()))
                        ResultadoEnvio.FALHA_PERMANENTE
                    }
                }
            }

            status == 429 || status in 500..599 -> {
                logger.warn("FCM retornou {} para destino {} (falha transitória)", status, registro.id)
                ResultadoEnvio.FALHA_TRANSITORIA
            }

            else -> {
                logger.warn("FCM retornou {} inesperado para destino {}", status, registro.id)
                ResultadoEnvio.FALHA_PERMANENTE
            }
        }
    }

    /** Verifica se o `errorCode` está presente nos `details` da resposta de erro do FCM. */
    private fun contemErro(body: String, errorCode: String): Boolean {
        return try {
            val root = mapper.readTree(body)
            val details = root.path("error").path("details")
            if (details.isArray) {
                details.any { it.path("errorCode").asText() == errorCode }
            } else {
                root.path("error").path("status").asText().equals(errorCode, ignoreCase = true) ||
                    body.contains(errorCode)
            }
        } catch (e: Exception) {
            body.contains(errorCode)
        }
    }

    /** Trunca o corpo para evitar vazar informação sensível nos logs. */
    private fun censurado(body: String) = body.take(200)

    private fun carregarCredenciais(): Pair<FcmCredenciais?, FcmTokenService?> {
        val path = properties.fcmCredenciaisPath.trim()
        if (path.isEmpty()) return null to null

        return try {
            val json = Files.readString(Paths.get(path))
            val creds = mapper.readValue(json, FcmCredenciais::class.java)
            if (creds.projectId.isBlank() || creds.clientEmail.isBlank() ||
                creds.privateKey.isBlank() || creds.tokenUri.isBlank()
            ) {
                logger.warn("JSON da conta de serviço Firebase está incompleto; canal FCM desligado")
                return null to null
            }
            val svc = FcmTokenService(creds, httpClient, mapper)
            creds to svc
        } catch (e: Exception) {
            logger.warn("Não foi possível carregar a credencial FCM de '{}': {}; canal desligado", path, e.message)
            null to null
        }
    }
}
