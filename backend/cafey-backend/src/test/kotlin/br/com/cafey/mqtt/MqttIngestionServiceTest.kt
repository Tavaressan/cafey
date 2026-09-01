package br.com.cafey.mqtt

import br.com.cafey.config.AwsIotProperties
import br.com.cafey.device.Dispositivo
import br.com.cafey.device.DispositivoRepository
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class MqttIngestionServiceTest {

    @Mock
    private lateinit var dispositivoRepository: DispositivoRepository

    @Mock
    private lateinit var eventPublisher: ApplicationEventPublisher

    private lateinit var ingestionService: MqttIngestionService
    private lateinit var clientService: MqttClientService
    private lateinit var objectMapper: ObjectMapper
    private val dispositivoId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        ingestionService = MqttIngestionService(dispositivoRepository, eventPublisher)
        objectMapper = jacksonObjectMapper()
        val properties = AwsIotProperties()
        clientService = MqttClientService(properties, ingestionService, objectMapper, null)
    }

    @Test
    fun `should process estado message and update dispositivo`() {
        val disp = Dispositivo(id = dispositivoId, nome = "Cafeteira", estado = "DESLIGADO")
        `when`(dispositivoRepository.findById(dispositivoId)).thenReturn(Optional.of(disp))
        `when`(dispositivoRepository.save(any(Dispositivo::class.java))).thenAnswer { it.getArgument(0) }

        val payload = EstadoPayload(estado = "PREPARANDO", desde = Instant.now(), firmware = "1.0.0")
        ingestionService.processarEstado(dispositivoId, payload)

        assertEquals("PREPARANDO", disp.estado)
        assertNotNull(disp.ultimoVisto)
        verify(dispositivoRepository).save(disp)
    }

    @Test
    fun `should process saude message and publish event when coming back online`() {
        val disp = Dispositivo(id = dispositivoId, nome = "Cafeteira", online = false)
        `when`(dispositivoRepository.findById(dispositivoId)).thenReturn(Optional.of(disp))
        `when`(dispositivoRepository.save(any(Dispositivo::class.java))).thenAnswer { it.getArgument(0) }

        val payload = SaudePayload(online = true, uptimeS = 3600, rssi = -65)
        ingestionService.processarSaude(dispositivoId, payload)

        assertTrue(disp.online)
        val captor = ArgumentCaptor.forClass(DispositivoOnlineEvent::class.java)
        verify(eventPublisher).publishEvent(captor.capture())
        assertEquals(dispositivoId, captor.value.dispositivoId)
    }

    @Test
    fun `should parse incoming topic and route to state ingestion`() {
        val disp = Dispositivo(id = dispositivoId, nome = "Cafeteira", estado = "DESLIGADO")
        `when`(dispositivoRepository.findById(dispositivoId)).thenReturn(Optional.of(disp))
        `when`(dispositivoRepository.save(any(Dispositivo::class.java))).thenAnswer { it.getArgument(0) }

        val json = """{"estado":"LIGADO","desde":"2026-09-01T15:00:00Z","firmware":"1.0.0"}"""
        clientService.handleIncomingMessage("dispositivos/$dispositivoId/estado", json)

        assertEquals("LIGADO", disp.estado)
    }

    @Test
    fun `should parse incoming topic and route to health ingestion`() {
        val disp = Dispositivo(id = dispositivoId, nome = "Cafeteira", online = false)
        `when`(dispositivoRepository.findById(dispositivoId)).thenReturn(Optional.of(disp))
        `when`(dispositivoRepository.save(any(Dispositivo::class.java))).thenAnswer { it.getArgument(0) }

        val json = """{"online":true,"uptimeS":120,"rssi":-70}"""
        clientService.handleIncomingMessage("dispositivos/$dispositivoId/saude", json)

        assertTrue(disp.online)
    }
}
