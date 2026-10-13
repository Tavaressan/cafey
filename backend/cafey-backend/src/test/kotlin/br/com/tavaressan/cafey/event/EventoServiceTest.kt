package br.com.tavaressan.cafey.event

import br.com.tavaressan.cafey.device.Dispositivo
import br.com.tavaressan.cafey.device.DispositivoRepository
import br.com.tavaressan.cafey.device.PapelDispositivo
import br.com.tavaressan.cafey.device.UsuarioDispositivo
import br.com.tavaressan.cafey.device.UsuarioDispositivoRepository
import br.com.tavaressan.cafey.exception.BadCredentialsException
import br.com.tavaressan.cafey.user.Usuario
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
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
    fun `should compute sequenciaManhasDias from timestamps repository in estatisticas`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(usuarioId, dispositivoId)).thenReturn(ownerLink)
        `when`(dispositivoRepository.findById(dispositivoId)).thenReturn(Optional.of(device))
        `when`(eventoPreparoRepository.countPorOrigem(dispositivoId)).thenReturn(emptyList())
        `when`(eventoPreparoRepository.sumDuracaoConcluidos(dispositivoId)).thenReturn(0L)
        `when`(eventoPreparoRepository.findTimestampsPreparosConcluidos(dispositivoId)).thenReturn(emptyList())

        val estatisticas = service.obterEstatisticas(dispositivoId, usuarioId)

        // device.timezone tem o padrão "America/Sao_Paulo" e não há timestamps — sequência zero,
        // igual a SequenciaManhas.calcular(emptyList(), ...). O foco aqui é a integração
        // (repositório -> ZoneId do dispositivo -> SequenciaManhas), já coberta em detalhe por
        // SequenciaManhasTest.
        assertEquals(0, estatisticas.sequenciaManhasDias)
        verify(eventoPreparoRepository).findTimestampsPreparosConcluidos(dispositivoId)
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
    fun `should compute enxague and filtro counters since their last baixa`() {
        val baixaEnxague = Instant.parse("2026-09-20T08:00:00Z")
        val baixaFiltro = Instant.parse("2026-08-01T08:00:00Z")
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(usuarioId, dispositivoId)).thenReturn(ownerLink)
        `when`(dispositivoRepository.findById(dispositivoId)).thenReturn(Optional.of(device))
        `when`(eventoPreparoRepository.findUltimaBaixaEm(dispositivoId, "BAIXA_ENXAGUE")).thenReturn(baixaEnxague)
        `when`(eventoPreparoRepository.findUltimaBaixaEm(dispositivoId, "BAIXA_FILTRO")).thenReturn(baixaFiltro)
        `when`(eventoPreparoRepository.countPreparosConcluidosDesde(dispositivoId, baixaEnxague)).thenReturn(34L)
        `when`(eventoPreparoRepository.countPreparosConcluidosDesde(dispositivoId, baixaFiltro)).thenReturn(210L)

        val cuidados = service.obterCuidados(dispositivoId, usuarioId)

        assertEquals(34, cuidados.enxague.contadorPreparos)
        assertEquals(40, cuidados.enxague.limiarPreparos)
        assertFalse(cuidados.enxague.precisaAtencao)
        assertEquals(85.0, cuidados.enxague.percentualUso)
        assertEquals(210, cuidados.filtro.contadorPreparos)
        assertEquals(300, cuidados.filtro.limiarPreparos)
        assertFalse(cuidados.filtro.precisaAtencao)
        assertEquals(70.0, cuidados.filtro.percentualUso)
        assertEquals(5, cuidados.descalcificacao.contadorPreparos)
        assertEquals(10, cuidados.descalcificacao.limiarDescalcificacao)
    }

    @Test
    fun `should count every brew when there is no baixa yet and flag the limiar as reached`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(usuarioId, dispositivoId)).thenReturn(ownerLink)
        `when`(dispositivoRepository.findById(dispositivoId)).thenReturn(Optional.of(device))
        `when`(eventoPreparoRepository.findUltimaBaixaEm(dispositivoId, "BAIXA_ENXAGUE")).thenReturn(null)
        `when`(eventoPreparoRepository.findUltimaBaixaEm(dispositivoId, "BAIXA_FILTRO")).thenReturn(null)
        `when`(eventoPreparoRepository.countPreparosConcluidosDesde(dispositivoId, Instant.EPOCH)).thenReturn(40L)

        val cuidados = service.obterCuidados(dispositivoId, usuarioId)

        // 40 de 40 preparos: o limiar é atingido (>=), igual à descalcificação.
        assertTrue(cuidados.enxague.precisaAtencao)
        assertEquals(100.0, cuidados.enxague.percentualUso)
        assertFalse(cuidados.filtro.precisaAtencao)
    }

    @Test
    fun `should highlight the cuidado with the highest counter to limiar ratio`() {
        val baixaEnxague = Instant.parse("2026-09-20T08:00:00Z")
        val baixaFiltro = Instant.parse("2026-08-01T08:00:00Z")
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(usuarioId, dispositivoId)).thenReturn(ownerLink)
        `when`(dispositivoRepository.findById(dispositivoId)).thenReturn(Optional.of(device))
        `when`(eventoPreparoRepository.findUltimaBaixaEm(dispositivoId, "BAIXA_ENXAGUE")).thenReturn(baixaEnxague)
        `when`(eventoPreparoRepository.findUltimaBaixaEm(dispositivoId, "BAIXA_FILTRO")).thenReturn(baixaFiltro)
        `when`(eventoPreparoRepository.countPreparosConcluidosDesde(dispositivoId, baixaEnxague)).thenReturn(10L)
        `when`(eventoPreparoRepository.countPreparosConcluidosDesde(dispositivoId, baixaFiltro)).thenReturn(290L)

        // enxágue 10/40 = 0,25; filtro 290/300 = 0,97; descalcificação 5/10 = 0,5.
        assertEquals(TipoCuidado.FILTRO, service.obterCuidados(dispositivoId, usuarioId).destaque)
    }

    @Test
    fun `should record a baixa marker for enxague and zero its counter`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(usuarioId, dispositivoId)).thenReturn(ownerLink)

        val status = service.darBaixaEnxague(dispositivoId, usuarioId)

        val captor = ArgumentCaptor.forClass(EventoPreparo::class.java)
        verify(eventoPreparoRepository).save(captor.capture())
        val marcador = captor.value
        assertEquals("BAIXA_ENXAGUE", marcador.tipo)
        assertEquals("BAIXA", marcador.resultado)
        assertEquals(device, marcador.dispositivo)
        assertTrue(marcador.eventoId.startsWith("BAIXA_ENXAGUE-"))
        assertEquals(0, status.contadorPreparos)
        assertEquals(40, status.limiarPreparos)
        assertFalse(status.precisaAtencao)
        assertEquals(0.0, status.percentualUso)
        // A baixa dos novos cuidados não mexe no contador de descalcificação do dispositivo.
        assertEquals(5, device.contadorPreparos)
    }

    @Test
    fun `should record a baixa marker for filtro with its own limiar`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(usuarioId, dispositivoId)).thenReturn(ownerLink)

        val status = service.darBaixaFiltro(dispositivoId, usuarioId)

        val captor = ArgumentCaptor.forClass(EventoPreparo::class.java)
        verify(eventoPreparoRepository).save(captor.capture())
        assertEquals("BAIXA_FILTRO", captor.value.tipo)
        assertEquals(300, status.limiarPreparos)
        assertEquals(0, status.contadorPreparos)
    }

    @Test
    fun `should reject baixa of enxague and filtro for non owners`() {
        val convidado = UsuarioDispositivo(usuario = user, dispositivo = device, papel = PapelDispositivo.CONVIDADO)
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(usuarioId, dispositivoId)).thenReturn(convidado)

        assertThrows<BadCredentialsException> { service.darBaixaEnxague(dispositivoId, usuarioId) }
        assertThrows<BadCredentialsException> { service.darBaixaFiltro(dispositivoId, usuarioId) }
        verify(eventoPreparoRepository, never()).save(any(EventoPreparo::class.java))
    }

    @Test
    fun `should not let ingestion forge a baixa marker`() {
        `when`(dispositivoRepository.findById(dispositivoId)).thenReturn(Optional.of(device))

        val req = IngestaoEventoRequest(
            eventoId = "evt-forjado",
            tipo = "BAIXA_ENXAGUE",
            resultado = "BAIXA",
            origem = "APP",
            timestamp = Instant.now()
        )

        assertNull(service.ingestar(dispositivoId, req))
        verify(eventoPreparoRepository, never()).save(any(EventoPreparo::class.java))
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
