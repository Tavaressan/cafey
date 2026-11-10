package br.com.tavaressan.cafey.mqtt

import br.com.tavaressan.cafey.config.AwsIotProperties
import br.com.tavaressan.cafey.device.Dispositivo
import br.com.tavaressan.cafey.device.DispositivoRepository
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.*
import br.com.tavaressan.cafey.event.EventoService
import br.com.tavaressan.cafey.event.IngestaoEventoRequest
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class MqttIngestionServiceTest {

    @Mock
    private lateinit var dispositivoRepository: DispositivoRepository

    @Mock
    private lateinit var eventPublisher: ApplicationEventPublisher

    @Mock
    private lateinit var eventoService: EventoService

    private lateinit var ingestionService: MqttIngestionService
    private lateinit var clientService: MqttClientService
    private lateinit var objectMapper: ObjectMapper
    private val dispositivoId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        ingestionService = MqttIngestionService(dispositivoRepository, eventPublisher, eventoService)
        objectMapper = jacksonObjectMapper()
        val properties = AwsIotProperties()
        clientService = MqttClientService(properties, ingestionService, objectMapper, null)
    }

    @Test
    fun `should process estado message and update dispositivo`() {
        val disp = Dispositivo(id = dispositivoId, nome = "Cafeteira", estado = "DESLIGADO")
        `when`(dispositivoRepository.findById(dispositivoId)).thenReturn(Optional.of(disp))
        `when`(dispositivoRepository.save(any(Dispositivo::class.java))).thenAnswer { it.getArgument(0) }

        val inicio = Instant.parse("2026-09-01T15:00:00Z")
        val payload = EstadoPayload(estado = "PREPARANDO", desde = inicio, firmware = "1.0.0")
        ingestionService.processarEstado(dispositivoId, payload)

        assertEquals("PREPARANDO", disp.estado)
        assertEquals(inicio, disp.ultimoVisto)
        assertEquals(inicio, disp.preparoIniciadoEm)
        assertEquals("1.0.0", disp.firmware)
        verify(dispositivoRepository).save(disp)
    }

    @Test
    fun `should clear preparoIniciadoEm when estado leaves brewing`() {
        val inicio = Instant.parse("2026-09-01T15:00:00Z")
        val disp = Dispositivo(
            id = dispositivoId,
            nome = "Cafeteira",
            estado = "PREPARANDO",
            preparoIniciadoEm = inicio,
        )
        `when`(dispositivoRepository.findById(dispositivoId)).thenReturn(Optional.of(disp))
        `when`(dispositivoRepository.save(any(Dispositivo::class.java))).thenAnswer { it.getArgument(0) }

        ingestionService.processarEstado(dispositivoId, EstadoPayload(estado = "PRONTO", desde = null))

        assertEquals("PRONTO", disp.estado)
        assertNull(disp.preparoIniciadoEm)
    }

    @Test
    fun `should keep preparoIniciadoEm when brewing message repeats without desde`() {
        val inicio = Instant.parse("2026-09-01T15:00:00Z")
        val disp = Dispositivo(
            id = dispositivoId,
            nome = "Cafeteira",
            estado = "PREPARANDO",
            preparoIniciadoEm = inicio,
        )
        `when`(dispositivoRepository.findById(dispositivoId)).thenReturn(Optional.of(disp))
        `when`(dispositivoRepository.save(any(Dispositivo::class.java))).thenAnswer { it.getArgument(0) }

        ingestionService.processarEstado(dispositivoId, EstadoPayload(estado = "PREPARANDO", desde = null))

        assertEquals("PREPARANDO", disp.estado)
        assertEquals(inicio, disp.preparoIniciadoEm)
    }

    @Test
    fun `should process saude message and publish event when coming back online`() {
        val disp = Dispositivo(id = dispositivoId, nome = "Cafeteira", online = false)
        `when`(dispositivoRepository.findById(dispositivoId)).thenReturn(Optional.of(disp))
        `when`(dispositivoRepository.save(any(Dispositivo::class.java))).thenAnswer { it.getArgument(0) }

        val payload = SaudePayload(online = true, uptimeS = 3600, rssi = -65)
        ingestionService.processarSaude(dispositivoId, payload)

        assertTrue(disp.online)
        assertEquals(-65, disp.rssi)
        assertEquals(3600L, disp.uptimeS)
        val captor = ArgumentCaptor.forClass(DispositivoOnlineEvent::class.java)
        verify(eventPublisher).publishEvent(captor.capture())
        assertEquals(dispositivoId, captor.value.dispositivoId)
    }

    @Test
    fun `should store vinculo code with backend calculated expiration`() {
        val disp = Dispositivo(id = dispositivoId, nome = "Cafeteira")
        `when`(dispositivoRepository.findById(dispositivoId)).thenReturn(Optional.of(disp))
        `when`(dispositivoRepository.save(any(Dispositivo::class.java))).thenAnswer { it.getArgument(0) }

        val antes = Instant.now()
        ingestionService.processarVinculo(dispositivoId, VinculoPayload(codigo = "123456"))
        val depois = Instant.now()

        assertEquals("123456", disp.codigoVinculo)
        assertNotNull(disp.codigoVinculoExpiraEm)
        val expiraEm = disp.codigoVinculoExpiraEm!!
        val esperadoMin = antes.plusSeconds(MqttIngestionService.VINCULO_TTL_SEGUNDOS)
        val esperadoMax = depois.plusSeconds(MqttIngestionService.VINCULO_TTL_SEGUNDOS)
        assertFalse(expiraEm.isBefore(esperadoMin))
        assertFalse(expiraEm.isAfter(esperadoMax))
        verify(dispositivoRepository).save(disp)
    }

    @Test
    fun `should overwrite previous vinculo code when window is reopened`() {
        val disp = Dispositivo(
            id = dispositivoId,
            nome = "Cafeteira",
            codigoVinculo = "111111",
            codigoVinculoExpiraEm = Instant.now().plusSeconds(10)
        )
        `when`(dispositivoRepository.findById(dispositivoId)).thenReturn(Optional.of(disp))
        `when`(dispositivoRepository.save(any(Dispositivo::class.java))).thenAnswer { it.getArgument(0) }

        ingestionService.processarVinculo(dispositivoId, VinculoPayload(codigo = "222222"))

        assertEquals("222222", disp.codigoVinculo)
        assertTrue(disp.codigoVinculoExpiraEm!!.isAfter(Instant.now().plusSeconds(100)))
    }

    @Test
    fun `should ignore vinculo code for unknown device`() {
        `when`(dispositivoRepository.findById(dispositivoId)).thenReturn(Optional.empty())

        ingestionService.processarVinculo(dispositivoId, VinculoPayload(codigo = "123456"))

        verify(dispositivoRepository, never()).save(any(Dispositivo::class.java))
    }

    @Test
    fun `should ignore empty vinculo code`() {
        val disp = Dispositivo(id = dispositivoId, nome = "Cafeteira", codigoVinculo = "123456")
        `when`(dispositivoRepository.findById(dispositivoId)).thenReturn(Optional.of(disp))

        ingestionService.processarVinculo(dispositivoId, VinculoPayload(codigo = "  "))

        assertEquals("123456", disp.codigoVinculo)
        verify(dispositivoRepository, never()).save(any(Dispositivo::class.java))
    }

    @Test
    fun `should parse incoming vinculo topic and route to ingestion`() {
        val disp = Dispositivo(id = dispositivoId, nome = "Cafeteira")
        `when`(dispositivoRepository.findById(dispositivoId)).thenReturn(Optional.of(disp))
        `when`(dispositivoRepository.save(any(Dispositivo::class.java))).thenAnswer { it.getArgument(0) }

        clientService.handleIncomingMessage("dispositivos/$dispositivoId/vinculo", """{"codigo":"654321"}""")

        assertEquals("654321", disp.codigoVinculo)
        assertNotNull(disp.codigoVinculoExpiraEm)
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

    @Test
    fun `should parse incoming topic and route to event ingestion`() {
        val json = """{"eventoId":"evt-99","tipo":"PREPARO","resultado":"CONCLUIDO","origem":"APP","duracaoS":300,"timestamp":"2026-09-01T15:00:00Z"}"""
        clientService.handleIncomingMessage("dispositivos/$dispositivoId/eventos", json)

        val dummy = IngestaoEventoRequest("e", "PREPARO", "CONCLUIDO", "APP", 0, Instant.now())
        verify(eventoService).ingestar(any() ?: dispositivoId, any() ?: dummy)
    }

    @Test
    fun `should ignore duplicate event when data integrity violation occurs concurrently`() {
        `when`(eventoService.ingestar(any() ?: dispositivoId, any() ?: IngestaoEventoRequest("e", "PREPARO", "CONCLUIDO", "APP", 0, Instant.now())))
            .thenThrow(DataIntegrityViolationException("duplicate key value violates unique constraint"))

        val payload = EventoPayload(
            eventoId = "evt-duplicado",
            tipo = "PREPARO",
            resultado = "CONCLUIDO",
            origem = "APP",
            duracaoS = 300,
            timestamp = Instant.now()
        )

        assertDoesNotThrow {
            ingestionService.processarEvento(dispositivoId, payload)
        }
    }
}
