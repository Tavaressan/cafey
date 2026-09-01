package br.com.cafey.config

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
        val certPath = properties.certificatePath ?: return null
        val keyPath = properties.privateKeyPath ?: return null

        val builder = AwsIotMqttConnectionBuilder.newMtlsBuilderFromPath(certPath, keyPath)
            .withEndpoint(properties.endpoint)
            .withPort(properties.port)
            .withClientId(properties.clientId)
            .withCleanSession(false)

        properties.rootCaPath?.let { builder.withCertificateAuthorityFromPath(null, it) }

        return builder.build()
    }
}
