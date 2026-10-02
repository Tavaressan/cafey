package br.com.tavaressan.cafey.schedule

import br.com.tavaressan.cafey.config.AwsIotProperties
import br.com.tavaressan.cafey.device.Dispositivo
import br.com.tavaressan.cafey.device.DispositivoRepository
import br.com.tavaressan.cafey.device.PapelDispositivo
import br.com.tavaressan.cafey.device.UsuarioDispositivo
import br.com.tavaressan.cafey.device.UsuarioDispositivoRepository
import br.com.tavaressan.cafey.mqtt.AgendamentosPayload
import br.com.tavaressan.cafey.mqtt.DispositivoOnlineEvent
import br.com.tavaressan.cafey.mqtt.MqttClientService
import br.com.tavaressan.cafey.user.Usuario
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.context.ApplicationEventPublisher
import software.amazon.awssdk.crt.mqtt.QualityOfService
import java.time.LocalTime
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class AgendamentoServiceTest {

    @Mock
    private lateinit var agendamentoRepository: AgendamentoRepository

    @Mock
    private lateinit var dispositivoRepository: DispositivoRepository

    @Mock
    private lateinit var usuarioDispositivoRepository: UsuarioDispositivoRepository

    @Mock
    private lateinit var eventPublisher: ApplicationEventPublisher

    @Mock
    private lateinit var mqttClientService: MqttClientService

    private lateinit var service: AgendamentoService
    private lateinit var syncListener: AgendamentosMqttSyncListener

    private val usuarioId = UUID.randomUUID()
    private val dispositivoId = UUID.randomUUID()
    private val agendamentoId = UUID.randomUUID()

    private lateinit var user: Usuario
    private lateinit var device: Dispositivo
    private lateinit var link: UsuarioDispositivo
    private lateinit var agendamento: Agendamento

    @BeforeEach
    fun setUp() {
        service = AgendamentoService(agendamentoRepository, dispositivoRepository, usuarioDispositivoRepository, eventPublisher)
        syncListener = AgendamentosMqttSyncListener(dispositivoRepository, agendamentoRepository, mqttClientService)

        user = Usuario(id = usuarioId, nome = "User", email = "user@cafey.com", senhaHash = "hash")
        device = Dispositivo(id = dispositivoId, nome = "Cafeteira", versaoAgendamentos = 1)
        link = UsuarioDispositivo(usuario = user, dispositivo = device, papel = PapelDispositivo.PROPRIETARIO)
        agendamento = Agendamento(id = agendamentoId, dispositivo = device, hora = LocalTime.of(7, 0), diasSemana = 62, ativo = true)
    }

    @Test
    fun `should create agendamento and increment versao`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(usuarioId, dispositivoId)).thenReturn(link)
        `when`(dispositivoRepository.findById(dispositivoId)).thenReturn(Optional.of(device))
        `when`(agendamentoRepository.save(any(Agendamento::class.java))).thenAnswer {
            val a = it.getArgument<Agendamento>(0)
            a.id = agendamentoId
            a
        }

        val req = CriarAgendamentoRequest(hora = "07:30", diasSemana = 62, ativo = true)
        val res = service.criar(dispositivoId, req, usuarioId)

        assertEquals("07:30", res.hora)
        assertEquals(2, device.versaoAgendamentos)
        verify(eventPublisher).publishEvent(any(AgendamentosAlteradosEvent::class.java))
    }

    @Test
    fun `should persist duracaoPreparoS when creating agendamento`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(usuarioId, dispositivoId)).thenReturn(link)
        `when`(dispositivoRepository.findById(dispositivoId)).thenReturn(Optional.of(device))
        `when`(agendamentoRepository.save(any(Agendamento::class.java))).thenAnswer {
            val a = it.getArgument<Agendamento>(0)
            a.id = agendamentoId
            a
        }

        val req = CriarAgendamentoRequest(hora = "07:30", diasSemana = 62, duracaoPreparoS = 480)
        val res = service.criar(dispositivoId, req, usuarioId)

        assertEquals(480, res.duracaoPreparoS)
    }

    @Test
    fun `should create agendamento without duracaoPreparoS when omitted`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(usuarioId, dispositivoId)).thenReturn(link)
        `when`(dispositivoRepository.findById(dispositivoId)).thenReturn(Optional.of(device))
        `when`(agendamentoRepository.save(any(Agendamento::class.java))).thenAnswer {
            val a = it.getArgument<Agendamento>(0)
            a.id = agendamentoId
            a
        }

        val res = service.criar(dispositivoId, CriarAgendamentoRequest(hora = "07:30", diasSemana = 62), usuarioId)

        assertNull(res.duracaoPreparoS)
    }

    @Test
    fun `should update duracaoPreparoS when provided`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(usuarioId, dispositivoId)).thenReturn(link)
        `when`(agendamentoRepository.findById(agendamentoId)).thenReturn(Optional.of(agendamento))
        `when`(agendamentoRepository.save(any(Agendamento::class.java))).thenAnswer { it.getArgument<Agendamento>(0) }

        val res = service.atualizar(dispositivoId, agendamentoId, AtualizarAgendamentoRequest(duracaoPreparoS = 600), usuarioId)

        assertEquals(600, res.duracaoPreparoS)
        assertEquals(600, agendamento.duracaoPreparoS)
    }

    @Test
    fun `should keep stored duracaoPreparoS when update omits it`() {
        agendamento.duracaoPreparoS = 360
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(usuarioId, dispositivoId)).thenReturn(link)
        `when`(agendamentoRepository.findById(agendamentoId)).thenReturn(Optional.of(agendamento))
        `when`(agendamentoRepository.save(any(Agendamento::class.java))).thenAnswer { it.getArgument<Agendamento>(0) }

        // Toggle de ativo não envia a duração: null significa "não alterar".
        val res = service.atualizar(dispositivoId, agendamentoId, AtualizarAgendamentoRequest(ativo = false), usuarioId)

        assertEquals(360, res.duracaoPreparoS)
    }

    @Test
    fun `should publish agendamento duration with fallback to dispositivo duration`() {
        device.duracaoPreparoS = 300
        val comDuracao = Agendamento(
            id = UUID.randomUUID(), dispositivo = device, hora = LocalTime.of(7, 0), diasSemana = 62, duracaoPreparoS = 480
        )
        val semDuracao = Agendamento(
            id = UUID.randomUUID(), dispositivo = device, hora = LocalTime.of(8, 0), diasSemana = 62, duracaoPreparoS = null
        )
        `when`(dispositivoRepository.findById(dispositivoId)).thenReturn(Optional.of(device))
        `when`(agendamentoRepository.findByDispositivoId(dispositivoId)).thenReturn(listOf(comDuracao, semDuracao))

        syncListener.sincronizarAgendamentos(dispositivoId)

        val captor = ArgumentCaptor.forClass(AgendamentosPayload::class.java)
        verify(mqttClientService).publish(
            anyString() ?: "",
            captor.capture() ?: AgendamentosPayload(0, "", 0, emptyList()),
            any() ?: QualityOfService.AT_LEAST_ONCE,
            anyBoolean()
        )

        val items = captor.value.agendamentos
        assertEquals(480, items[0].duracaoS)
        assertEquals(300, items[1].duracaoS)
    }

    @Test
    fun `should publish retain agendamentos on dispositivo online event`() {
        `when`(dispositivoRepository.findById(dispositivoId)).thenReturn(Optional.of(device))
        `when`(agendamentoRepository.findByDispositivoId(dispositivoId)).thenReturn(listOf(agendamento))

        syncListener.onDispositivoOnline(DispositivoOnlineEvent(dispositivoId))

        val captor = ArgumentCaptor.forClass(AgendamentosPayload::class.java)
        verify(mqttClientService).publish(
            anyString() ?: "",
            captor.capture() ?: AgendamentosPayload(0, "", 0, emptyList()),
            any() ?: QualityOfService.AT_LEAST_ONCE,
            anyBoolean()
        )

        val payload = captor.value
        assertEquals(device.versaoAgendamentos, payload.versao)
        assertEquals(1, payload.agendamentos.size)
        assertEquals("07:00", payload.agendamentos[0].hora)
    }
}
