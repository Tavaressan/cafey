package br.com.cafey.config

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AwsIotPropertiesTest {

    @Test
    fun `should have valid default values`() {
        val props = AwsIotProperties()
        assertEquals("localhost", props.endpoint)
        assertEquals(8883, props.port)
        assertEquals("cafey-backend", props.clientId)
        assertEquals("cafey-backend", props.thingName)
        assertEquals(60, props.keepAliveSeconds)
        assertEquals(1000L, props.reconnectMinDelayMs)
        assertEquals(60000L, props.reconnectMaxDelayMs)
    }

    @Test
    fun `should generate correct topic names`() {
        assertEquals("dispositivos/+/estado", AwsIotProperties.TOPIC_ESTADO)
        assertEquals("dispositivos/+/eventos", AwsIotProperties.TOPIC_EVENTOS)
        assertEquals("dispositivos/+/saude", AwsIotProperties.TOPIC_SAUDE)
        assertEquals("dispositivos/dev-123/comando", AwsIotProperties.topicComando("dev-123"))
        assertEquals("dispositivos/dev-123/agendamentos", AwsIotProperties.topicAgendamentos("dev-123"))
    }
}
