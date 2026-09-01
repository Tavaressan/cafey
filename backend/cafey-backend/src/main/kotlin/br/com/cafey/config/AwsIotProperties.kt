package br.com.cafey.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "aws.iot")
class AwsIotProperties(
    var endpoint: String = "localhost",
    var port: Int = 8883,
    var clientId: String = "cafey-backend",
    var thingName: String = "cafey-backend",
    var certificatePath: String? = null,
    var privateKeyPath: String? = null,
    var rootCaPath: String? = null,
    var keepAliveSeconds: Int = 60,
    var pingTimeoutMs: Int = 3000,
    var connectTimeoutMs: Int = 5000,
    var reconnectMinDelayMs: Long = 1000,
    var reconnectMaxDelayMs: Long = 60000
) {
    companion object {
        const val TOPIC_ESTADO = "dispositivos/+/estado"
        const val TOPIC_EVENTOS = "dispositivos/+/eventos"
        const val TOPIC_SAUDE = "dispositivos/+/saude"

        fun topicComando(dispositivoId: String) = "dispositivos/$dispositivoId/comando"
        fun topicAgendamentos(dispositivoId: String) = "dispositivos/$dispositivoId/agendamentos"
    }
}
