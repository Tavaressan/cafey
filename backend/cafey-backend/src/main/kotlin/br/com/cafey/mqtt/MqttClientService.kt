package br.com.cafey.mqtt

import br.com.cafey.config.AwsIotProperties
import tools.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import software.amazon.awssdk.crt.mqtt.MqttClientConnection
import software.amazon.awssdk.crt.mqtt.QualityOfService
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

@Service
class MqttClientService(
    private val properties: AwsIotProperties,
    private val ingestionService: MqttIngestionService,
    private val objectMapper: ObjectMapper,
    @Autowired(required = false)
    private val connection: MqttClientConnection? = null
) {
    private val logger = LoggerFactory.getLogger(MqttClientService::class.java)
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    var currentBackoffMs: Long = properties.reconnectMinDelayMs
    var isConnected: Boolean = false

    private val estadoPattern = Pattern.compile("^dispositivos/([a-fA-F0-9-]+)/estado$")
    private val saudePattern = Pattern.compile("^dispositivos/([a-fA-F0-9-]+)/saude$")

    @PostConstruct
    fun start() {
        if (connection == null) {
            logger.info("Conexão MQTT desabilitada ou sem certificados configurados.")
            return
        }

        connectWithBackoff()
    }

    fun connectWithBackoff() {
        if (connection == null) return

        try {
            logger.info("Tentando conectar MQTT ao AWS IoT Core em {}:{}...", properties.endpoint, properties.port)
            connection.connect().whenComplete { _, throwable ->
                if (throwable != null) {
                    logger.error("Falha ao conectar MQTT: {}. Nova tentativa em {} ms", throwable.message, currentBackoffMs)
                    scheduleReconnect()
                } else {
                    logger.info("Conectado com sucesso ao AWS IoT Core!")
                    isConnected = true
                    currentBackoffMs = properties.reconnectMinDelayMs
                    subscribeToTopics()
                }
            }
        } catch (e: Exception) {
            logger.error("Exceção ao conectar MQTT: {}. Agendando reconexão...", e.message)
            scheduleReconnect()
        }
    }

    fun scheduleReconnect() {
        scheduler.schedule({
            currentBackoffMs = (currentBackoffMs * 2).coerceAtMost(properties.reconnectMaxDelayMs)
            connectWithBackoff()
        }, currentBackoffMs, TimeUnit.MILLISECONDS)
    }

    private fun subscribeToTopics() {
        val topics = listOf(
            AwsIotProperties.TOPIC_ESTADO,
            AwsIotProperties.TOPIC_SAUDE,
            AwsIotProperties.TOPIC_EVENTOS
        )

        for (topic in topics) {
            connection?.subscribe(topic, QualityOfService.AT_LEAST_ONCE) { message ->
                handleIncomingMessage(message.topic, String(message.payload, StandardCharsets.UTF_8))
            }
        }
    }

    fun handleIncomingMessage(topic: String, payloadStr: String) {
        try {
            val estadoMatcher = estadoPattern.matcher(topic)
            if (estadoMatcher.matches()) {
                val dispositivoId = UUID.fromString(estadoMatcher.group(1))
                val payload = objectMapper.readValue(payloadStr, EstadoPayload::class.java)
                ingestionService.processarEstado(dispositivoId, payload)
                return
            }

            val saudeMatcher = saudePattern.matcher(topic)
            if (saudeMatcher.matches()) {
                val dispositivoId = UUID.fromString(saudeMatcher.group(1))
                val payload = objectMapper.readValue(payloadStr, SaudePayload::class.java)
                ingestionService.processarSaude(dispositivoId, payload)
                return
            }
        } catch (e: Exception) {
            logger.error("Erro ao processar mensagem MQTT do tópico [{}]: {}", topic, e.message, e)
        }
    }

    fun publish(topic: String, payload: Any, qos: QualityOfService = QualityOfService.AT_LEAST_ONCE, retain: Boolean = false) {
        if (connection == null || !isConnected) {
            logger.warn("Não foi possível publicar no tópico [{}]: conexão inativa", topic)
            return
        }

        val json = objectMapper.writeValueAsString(payload)
        connection.publish(
            software.amazon.awssdk.crt.mqtt.MqttMessage(topic, json.toByteArray(StandardCharsets.UTF_8), qos, retain)
        )
    }

    @PreDestroy
    fun stop() {
        scheduler.shutdown()
        connection?.disconnect()
    }
}
