package br.com.tavaressan.cafey.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.crt.mqtt.MqttClientConnection
import software.amazon.awssdk.iot.AwsIotMqttConnectionBuilder

@Configuration
@EnableConfigurationProperties(AwsIotProperties::class)
class AwsIotConfig(
    private val properties: AwsIotProperties
) {

    @Bean
    @ConditionalOnProperty(prefix = "aws.iot", name = ["certificate-path", "private-key-path"])
    fun awsIotMqttConnection(): MqttClientConnection? {
        // Todos os 4 placeholders AWS_IOT_* têm default vazio em application-prod.yml (#159,
        // preserva o boot sem essas variáveis definidas) — string vazia conta como "ausente" em
        // qualquer um deles, não só null, senão o @ConditionalOnProperty (que só olha presença da
        // chave) deixaria passar e o builder do CRT falharia tentando abrir um path/endpoint vazio.
        val certPath = properties.certificatePath?.takeIf { it.isNotBlank() } ?: return null
        val keyPath = properties.privateKeyPath?.takeIf { it.isNotBlank() } ?: return null
        val endpoint = properties.endpoint.takeIf { it.isNotBlank() } ?: return null

        val builder = AwsIotMqttConnectionBuilder.newMtlsBuilderFromPath(certPath, keyPath)
            .withEndpoint(endpoint)
            .withPort(properties.port)
            .withClientId(properties.clientId)
            .withCleanSession(false)

        properties.rootCaPath?.takeIf { it.isNotBlank() }?.let { builder.withCertificateAuthorityFromPath(null, it) }

        return builder.build()
    }
}
