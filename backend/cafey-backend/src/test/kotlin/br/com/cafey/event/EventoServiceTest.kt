package br.com.cafey.event

import br.com.cafey.device.Dispositivo
import br.com.cafey.device.DispositivoRepository
import br.com.cafey.device.PapelDispositivo
import br.com.cafey.device.UsuarioDispositivo
import br.com.cafey.device.UsuarioDispositivoRepository
import br.com.cafey.exception.BadCredentialsException
import br.com.cafey.user.Usuario
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Duration
import java.time.Instant
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class EventoServiceTest {

    @Mock
    private lateinit var eventoPreparoRepository: EventoPreparoRepository

    @Mock
    private lateinit var dispositivoRepository: DispositivoRepository

    @Mock
    private lateinit var usuarioDispositivoRepository: UsuarioDispositivoRepository

    private lateinit var service: EventoService

    private val usuarioId = UUID.randomUUID()
    private val dispositivoId = UUID.randomUUID()

    private lateinit var user: Usuario
    private lateinit var device: Dispositivo
    private lateinit var ownerLink: UsuarioDispositivo

    @BeforeEach
    fun setUp() {
        service = EventoService(eventoPreparoRepository, dispositivoRepository, usuarioDispositivoRepository)

        user = Usuario(id = usuarioId, nome = "User", email = "user@cafey.com", senhaHash = "hash")
        device = Dispositivo(id = dispositivoId, nome = "Cafeteira", contadorPreparos = 5, limiarDescalcificacao = 10)
        ownerLink = UsuarioDispositivo(usuario = user, dispositivo = device, papel = PapelDispositivo.PROPRIETARIO)
    }

    @Test
    fun `should ingest event and increment contadorPreparos on CONCLUIDO`() {
        `when`(dispositivoRepository.findById(dispositivoId)).thenReturn(Optional.of(device))
        `when`(eventoPreparoRepository.existsByDispositivoIdAndEventoId(dispositivoId, "evt-1")).thenReturn(false)
        `when`(eventoPreparoRepository.save(any(EventoPreparo::class.java))).thenAnswer {
            val e = it.getArgument<EventoPreparo>(0)
            e.id = UUID.randomUUID()
            e
        }

        val req = IngestaoEventoRequest(
            eventoId = "evt-1",
            resultado = "CONCLUIDO",
            origem = "APP",
            duracaoS = 300,
            timestamp = Instant.now()
        )
        val res = service.ingestar(dispositivoId, req)

        assertNotNull(res)
        assertEquals("CONCLUIDO", res?.resultado)
        assertEquals(6, device.contadorPreparos)
        verify(dispositivoRepository).save(device)
    }

    @Test
    fun `should ignore duplicate event`() {
        `when`(dispositivoRepository.findById(dispositivoId)).thenReturn(Optional.of(device))
        `when`(eventoPreparoRepository.existsByDispositivoIdAndEventoId(dispositivoId, "evt-dup")).thenReturn(true)

        val req = IngestaoEventoRequest(
            eventoId = "evt-dup",
            resultado = "CONCLUIDO",
            origem = "BOTAO",
            duracaoS = 180,
            timestamp = Instant.now()
        )
        val res = service.ingestar(dispositivoId, req)

        assertNull(res)
        assertEquals(5, device.contadorPreparos)
        verify(eventoPreparoRepository, never()).save(any(EventoPreparo::class.java))
    }

    @Test
    fun `should calculate descaling status`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(usuarioId, dispositivoId)).thenReturn(ownerLink)
        `when`(dispositivoRepository.findById(dispositivoId)).thenReturn(Optional.of(device))

        val status = service.obterStatusDescalcificacao(dispositivoId, usuarioId)

        assertEquals(5, status.contadorPreparos)
        assertEquals(10, status.limiarDescalcificacao)
        assertFalse(status.precisaDescalcificar)
        assertEquals(50.0, status.percentualUso)
    }

    @Test
    fun `should reset descaling counter on baixa`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(usuarioId, dispositivoId)).thenReturn(ownerLink)
        `when`(dispositivoRepository.findById(dispositivoId)).thenReturn(Optional.of(device))

        val status = service.darBaixaDescalcificacao(dispositivoId, usuarioId)

        assertEquals(0, status.contadorPreparos)
        assertEquals(0, device.contadorPreparos)
        verify(dispositivoRepository).save(device)
    }

    @Test
    fun `should reject proxy ble with future timestamp`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(usuarioId, dispositivoId)).thenReturn(ownerLink)

        val req = ProxyBleEventosRequest(
            eventos = listOf(
                IngestaoEventoRequest(
                    eventoId = "evt-fut",
                    resultado = "CONCLUIDO",
                    origem = "APP",
                    duracaoS = 100,
                    timestamp = Instant.now().plus(Duration.ofHours(2))
                )
            )
        )

        assertThrows<BadCredentialsException> {
            service.processarProxyBle(dispositivoId, req, usuarioId)
        }
    }

    @Test
    fun `should reject proxy ble with timestamp older than 30 days`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(usuarioId, dispositivoId)).thenReturn(ownerLink)

        val req = ProxyBleEventosRequest(
            eventos = listOf(
                IngestaoEventoRequest(
                    eventoId = "evt-old",
                    resultado = "CONCLUIDO",
                    origem = "APP",
                    duracaoS = 100,
                    timestamp = Instant.now().minus(Duration.ofDays(31))
                )
            )
        )

        assertThrows<BadCredentialsException> {
            service.processarProxyBle(dispositivoId, req, usuarioId)
        }
    }
}
