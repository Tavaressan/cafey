package br.com.tavaressan.cafey.device

import br.com.tavaressan.cafey.event.EventoPreparo
import br.com.tavaressan.cafey.event.EventoPreparoRepository
import br.com.tavaressan.cafey.exception.BadCredentialsException
import br.com.tavaressan.cafey.exception.ConflitoException
import br.com.tavaressan.cafey.exception.RequisicaoInvalidaException
import br.com.tavaressan.cafey.exception.ResourceNotFoundException
import br.com.tavaressan.cafey.exception.ServicoIndisponivelException
import br.com.tavaressan.cafey.mapper.DispositivoMapper
import br.com.tavaressan.cafey.mqtt.ComandoPayload
import br.com.tavaressan.cafey.mqtt.MqttClientService
import br.com.tavaressan.cafey.schedule.AgendamentosAlteradosEvent
import br.com.tavaressan.cafey.user.Usuario
import br.com.tavaressan.cafey.user.UsuarioRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.ArgumentMatchers.nullable
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import software.amazon.awssdk.crt.mqtt.QualityOfService
import java.time.Instant
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class DispositivoServiceTest {

    @Mock
    private lateinit var dispositivoRepository: DispositivoRepository

    @Mock
    private lateinit var usuarioDispositivoRepository: UsuarioDispositivoRepository

    @Mock
    private lateinit var usuarioRepository: UsuarioRepository

    @Mock
    private lateinit var mqttClientService: MqttClientService

    @Mock
    private lateinit var eventPublisher: ApplicationEventPublisher

    @Mock
    private lateinit var eventoPreparoRepository: EventoPreparoRepository

    private lateinit var service: DispositivoService

    private val ownerId = UUID.randomUUID()
    private val guestId = UUID.randomUUID()
    private val deviceId = UUID.randomUUID()

    private lateinit var ownerUser: Usuario
    private lateinit var guestUser: Usuario
    private lateinit var device: Dispositivo
    private lateinit var ownerLink: UsuarioDispositivo
    private lateinit var guestLink: UsuarioDispositivo

    @BeforeEach
    fun setUp() {
        service = DispositivoService(dispositivoRepository, usuarioDispositivoRepository, usuarioRepository, mqttClientService, eventPublisher, DispositivoMapper(), eventoPreparoRepository)

        ownerUser = Usuario(id = ownerId, nome = "Owner", email = "owner@cafey.com", senhaHash = "hash")
        guestUser = Usuario(id = guestId, nome = "Guest", email = "guest@cafey.com", senhaHash = "hash")
        device = Dispositivo(id = deviceId, nome = "Cafeteira Britânia", duracaoPreparoS = 300)

        ownerLink = UsuarioDispositivo(usuario = ownerUser, dispositivo = device, papel = PapelDispositivo.PROPRIETARIO)
        guestLink = UsuarioDispositivo(usuario = guestUser, dispositivo = device, papel = PapelDispositivo.CONVIDADO)
    }

    @Test
    fun `should create device as owner`() {
        `when`(usuarioRepository.findById(ownerId)).thenReturn(Optional.of(ownerUser))
        `when`(dispositivoRepository.save(any(Dispositivo::class.java))).thenAnswer {
            val d = it.getArgument<Dispositivo>(0)
            d.id = deviceId
            d
        }
        `when`(usuarioDispositivoRepository.save(any(UsuarioDispositivo::class.java))).thenAnswer { it.getArgument(0) }

        val req = CriarDispositivoRequest(nome = "Minha Cafeteira")
        val res = service.criar(req, ownerId)

        assertEquals("Minha Cafeteira", res.nome)
        assertEquals(PapelDispositivo.PROPRIETARIO, res.papel)
        verify(usuarioDispositivoRepository).save(any(UsuarioDispositivo::class.java))
    }

    @Test
    fun `should link device by serial when it has no owner and code is valid`() {
        device.codigoVinculo = "123456"
        device.codigoVinculoExpiraEm = Instant.now().plusSeconds(60)
        `when`(usuarioRepository.findById(ownerId)).thenReturn(Optional.of(ownerUser))
        `when`(dispositivoRepository.findById(deviceId)).thenReturn(Optional.of(device))
        `when`(usuarioDispositivoRepository.findByDispositivoId(deviceId)).thenReturn(emptyList())
        `when`(usuarioDispositivoRepository.save(any(UsuarioDispositivo::class.java))).thenAnswer { it.getArgument(0) }
        `when`(dispositivoRepository.save(any(Dispositivo::class.java))).thenAnswer { it.getArgument(0) }

        val res = service.criar(
            CriarDispositivoRequest(nome = "Minha Cafeteira", serial = deviceId.toString(), codigoVinculo = "123456"),
            ownerId
        )

        assertEquals(deviceId, res.id)
        assertEquals(PapelDispositivo.PROPRIETARIO, res.papel)
        val captor = ArgumentCaptor.forClass(UsuarioDispositivo::class.java)
        verify(usuarioDispositivoRepository).save(captor.capture())
        assertEquals(ownerId, captor.value.usuario.id)
        assertEquals(PapelDispositivo.PROPRIETARIO, captor.value.papel)
        // Código de uso único: apagado após o vínculo.
        assertNull(device.codigoVinculo)
        assertNull(device.codigoVinculoExpiraEm)
        verify(dispositivoRepository).save(device)
    }

    @Test
    fun `should reject link without code when device has no owner`() {
        device.codigoVinculo = "123456"
        device.codigoVinculoExpiraEm = Instant.now().plusSeconds(60)
        `when`(usuarioRepository.findById(ownerId)).thenReturn(Optional.of(ownerUser))
        `when`(dispositivoRepository.findById(deviceId)).thenReturn(Optional.of(device))
        `when`(usuarioDispositivoRepository.findByDispositivoId(deviceId)).thenReturn(emptyList())

        assertThrows<RequisicaoInvalidaException> {
            service.criar(CriarDispositivoRequest(nome = "Minha", serial = deviceId.toString()), ownerId)
        }
        verify(usuarioDispositivoRepository, never()).save(any(UsuarioDispositivo::class.java))
        assertEquals("123456", device.codigoVinculo)
    }

    @Test
    fun `should reject link with wrong code when device has no owner`() {
        device.codigoVinculo = "123456"
        device.codigoVinculoExpiraEm = Instant.now().plusSeconds(60)
        `when`(usuarioRepository.findById(ownerId)).thenReturn(Optional.of(ownerUser))
        `when`(dispositivoRepository.findById(deviceId)).thenReturn(Optional.of(device))
        `when`(usuarioDispositivoRepository.findByDispositivoId(deviceId)).thenReturn(emptyList())

        assertThrows<RequisicaoInvalidaException> {
            service.criar(
                CriarDispositivoRequest(nome = "Minha", serial = deviceId.toString(), codigoVinculo = "654321"),
                ownerId
            )
        }
        verify(usuarioDispositivoRepository, never()).save(any(UsuarioDispositivo::class.java))
        assertEquals("123456", device.codigoVinculo)
    }

    @Test
    fun `should reject link when vinculo code expired`() {
        device.codigoVinculo = "123456"
        device.codigoVinculoExpiraEm = Instant.now().minusSeconds(1)
        `when`(usuarioRepository.findById(ownerId)).thenReturn(Optional.of(ownerUser))
        `when`(dispositivoRepository.findById(deviceId)).thenReturn(Optional.of(device))
        `when`(usuarioDispositivoRepository.findByDispositivoId(deviceId)).thenReturn(emptyList())

        assertThrows<RequisicaoInvalidaException> {
            service.criar(
                CriarDispositivoRequest(nome = "Minha", serial = deviceId.toString(), codigoVinculo = "123456"),
                ownerId
            )
        }
        verify(usuarioDispositivoRepository, never()).save(any(UsuarioDispositivo::class.java))
    }

    @Test
    fun `should reject link when device never opened a vinculo window`() {
        `when`(usuarioRepository.findById(ownerId)).thenReturn(Optional.of(ownerUser))
        `when`(dispositivoRepository.findById(deviceId)).thenReturn(Optional.of(device))
        `when`(usuarioDispositivoRepository.findByDispositivoId(deviceId)).thenReturn(emptyList())

        assertThrows<RequisicaoInvalidaException> {
            service.criar(
                CriarDispositivoRequest(nome = "Minha", serial = deviceId.toString(), codigoVinculo = "123456"),
                ownerId
            )
        }
        verify(usuarioDispositivoRepository, never()).save(any(UsuarioDispositivo::class.java))
    }

    @Test
    fun `should keep relink idempotent for the same owner without vinculo code`() {
        `when`(usuarioRepository.findById(ownerId)).thenReturn(Optional.of(ownerUser))
        `when`(dispositivoRepository.findById(deviceId)).thenReturn(Optional.of(device))
        `when`(usuarioDispositivoRepository.findByDispositivoId(deviceId)).thenReturn(listOf(ownerLink, guestLink))

        val res = service.criar(CriarDispositivoRequest(nome = "Outro Nome", serial = deviceId.toString()), ownerId)

        assertEquals(deviceId, res.id)
        assertEquals("Cafeteira Britânia", res.nome)
        verify(usuarioDispositivoRepository, never()).save(any(UsuarioDispositivo::class.java))
        verify(dispositivoRepository, never()).save(any(Dispositivo::class.java))
    }

    @Test
    fun `should reject serial of a device owned by another account`() {
        `when`(usuarioRepository.findById(guestId)).thenReturn(Optional.of(guestUser))
        `when`(dispositivoRepository.findById(deviceId)).thenReturn(Optional.of(device))
        `when`(usuarioDispositivoRepository.findByDispositivoId(deviceId)).thenReturn(listOf(ownerLink, guestLink))

        assertThrows<ConflitoException> {
            service.criar(CriarDispositivoRequest(nome = "Minha", serial = deviceId.toString()), guestId)
        }
        verify(usuarioDispositivoRepository, never()).save(any(UsuarioDispositivo::class.java))
    }

    @Test
    fun `should return not found for unknown serial without creating a device`() {
        `when`(usuarioRepository.findById(ownerId)).thenReturn(Optional.of(ownerUser))
        `when`(dispositivoRepository.findById(deviceId)).thenReturn(Optional.empty())

        assertThrows<ResourceNotFoundException> {
            service.criar(CriarDispositivoRequest(nome = "Minha", serial = deviceId.toString()), ownerId)
        }
        verify(dispositivoRepository, never()).save(any(Dispositivo::class.java))
    }

    @Test
    fun `should list devices for user`() {
        `when`(usuarioDispositivoRepository.findByUsuarioId(ownerId)).thenReturn(listOf(ownerLink))

        val list = service.listarDoUsuario(ownerId)
        assertEquals(1, list.size)
        assertEquals(deviceId, list[0].id)
    }

    @Test
    fun `buscar por nome consulta vinculos uma vez e conserva o papel de cada dispositivo`() {
        val secondDevice = Dispositivo(id = UUID.randomUUID(), nome = "Cafeteira Outra")
        val thirdDevice = Dispositivo(id = UUID.randomUUID(), nome = "Cafeteira Terceira")
        val secondOwnerLink = UsuarioDispositivo(usuario = ownerUser, dispositivo = secondDevice, papel = PapelDispositivo.PROPRIETARIO)
        val guestSharedLink = UsuarioDispositivo(usuario = ownerUser, dispositivo = thirdDevice, papel = PapelDispositivo.CONVIDADO)
        `when`(usuarioDispositivoRepository.findByUsuarioId(ownerId)).thenReturn(listOf(ownerLink, secondOwnerLink, guestSharedLink))
        `when`(dispositivoRepository.buscarPorNomeDoUsuario(ownerId, "Cafeteira"))
            .thenReturn(listOf(device, secondDevice, thirdDevice))

        val resultado = service.buscarPorNome(ownerId, "Cafeteira")

        assertEquals(listOf(PapelDispositivo.PROPRIETARIO, PapelDispositivo.PROPRIETARIO, PapelDispositivo.CONVIDADO), resultado.map { it.papel })
        verify(usuarioDispositivoRepository, times(1)).findByUsuarioId(ownerId)
    }

    @Test
    fun `should update device when owner`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(ownerId, deviceId)).thenReturn(ownerLink)
        `when`(dispositivoRepository.save(any(Dispositivo::class.java))).thenAnswer { it.getArgument(0) }

        val req = AtualizarDispositivoRequest(nome = "Cafeteira Atualizada", duracaoPreparoS = 240)
        val res = service.atualizar(deviceId, req, ownerId)

        assertEquals("Cafeteira Atualizada", res.nome)
        assertEquals(240, res.duracaoPreparoS)
        assertEquals(2, res.versaoAgendamentos)
        verify(eventPublisher).publishEvent(AgendamentosAlteradosEvent(deviceId))
    }

    @Test
    fun `should not republish schedules when duration does not change`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(ownerId, deviceId)).thenReturn(ownerLink)
        `when`(dispositivoRepository.save(any(Dispositivo::class.java))).thenAnswer { it.getArgument(0) }

        val req = AtualizarDispositivoRequest(nome = "Novo nome", duracaoPreparoS = device.duracaoPreparoS)
        val res = service.atualizar(deviceId, req, ownerId)

        assertEquals(1, res.versaoAgendamentos)
        verify(eventPublisher, never()).publishEvent(any(AgendamentosAlteradosEvent::class.java))
    }

    @Test
    fun `should not republish schedules when only the name changes`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(ownerId, deviceId)).thenReturn(ownerLink)
        `when`(dispositivoRepository.save(any(Dispositivo::class.java))).thenAnswer { it.getArgument(0) }

        val res = service.atualizar(deviceId, AtualizarDispositivoRequest(nome = "Só o nome"), ownerId)

        assertEquals("Só o nome", res.nome)
        assertEquals(1, res.versaoAgendamentos)
        verify(eventPublisher, never()).publishEvent(any(AgendamentosAlteradosEvent::class.java))
    }

    @Test
    fun `should republish schedules when timezone changes`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(ownerId, deviceId)).thenReturn(ownerLink)
        `when`(dispositivoRepository.save(any(Dispositivo::class.java))).thenAnswer { it.getArgument(0) }

        val res = service.atualizar(deviceId, AtualizarDispositivoRequest(timezone = " America/Manaus "), ownerId)

        assertEquals("America/Manaus", res.timezone)
        assertEquals(2, res.versaoAgendamentos)
        verify(eventPublisher).publishEvent(AgendamentosAlteradosEvent(deviceId))
    }

    @Test
    fun `should bump schedule version only once when timezone and duration change together`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(ownerId, deviceId)).thenReturn(ownerLink)
        `when`(dispositivoRepository.save(any(Dispositivo::class.java))).thenAnswer { it.getArgument(0) }

        val req = AtualizarDispositivoRequest(timezone = "America/Manaus", duracaoPreparoS = 240)
        val res = service.atualizar(deviceId, req, ownerId)

        assertEquals(2, res.versaoAgendamentos)
        verify(eventPublisher, times(1)).publishEvent(AgendamentosAlteradosEvent(deviceId))
    }

    @Test
    fun `should not republish schedules when timezone is the same after trim`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(ownerId, deviceId)).thenReturn(ownerLink)
        `when`(dispositivoRepository.save(any(Dispositivo::class.java))).thenAnswer { it.getArgument(0) }

        val res = service.atualizar(deviceId, AtualizarDispositivoRequest(timezone = " ${device.timezone} "), ownerId)

        assertEquals(1, res.versaoAgendamentos)
        verify(eventPublisher, never()).publishEvent(any(AgendamentosAlteradosEvent::class.java))
    }

    @Test
    fun `should reject update from guest`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(guestId, deviceId)).thenReturn(guestLink)

        val req = AtualizarDispositivoRequest(nome = "Nome Inválido")
        assertThrows<BadCredentialsException> {
            service.atualizar(deviceId, req, guestId)
        }
    }

    @Test
    fun `should delete device when owner`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(ownerId, deviceId)).thenReturn(ownerLink)

        service.excluir(deviceId, ownerId)
        verify(dispositivoRepository).delete(device)
    }

    @Test
    fun `should publish ligar command with qos1 and no retain`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(ownerId, deviceId)).thenReturn(ownerLink)
        `when`(
            mqttClientService.publish(
                anyString() ?: "",
                any() ?: Any(),
                any() ?: QualityOfService.AT_LEAST_ONCE,
                anyBoolean()
            )
        ).thenReturn(true)

        val res = service.comandar(deviceId, ComandoRequest(acao = "ligar"), ownerId)

        assertEquals(AcaoComando.LIGAR, res.acao)
        assertEquals(device.duracaoPreparoS, res.duracaoS)

        val captor = ArgumentCaptor.forClass(ComandoPayload::class.java)
        verify(mqttClientService).publish(
            eq("dispositivos/$deviceId/comando") ?: "",
            captor.capture() ?: ComandoPayload("", "", 0),
            any() ?: QualityOfService.AT_LEAST_ONCE,
            eq(false)
        )
        assertEquals("LIGAR", captor.value.acao)
        assertEquals(device.duracaoPreparoS, captor.value.duracaoS)
    }

    @Test
    fun `should allow guest to command device`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(guestId, deviceId)).thenReturn(guestLink)
        `when`(
            mqttClientService.publish(
                anyString() ?: "",
                any() ?: Any(),
                any() ?: QualityOfService.AT_LEAST_ONCE,
                anyBoolean()
            )
        ).thenReturn(true)

        val res = service.comandar(deviceId, ComandoRequest(acao = "DESLIGAR"), guestId)

        assertEquals(AcaoComando.DESLIGAR, res.acao)
    }

    @Test
    fun `should reject command for non-existing device with 404`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(ownerId, deviceId)).thenReturn(null)

        assertThrows<ResourceNotFoundException> {
            service.comandar(deviceId, ComandoRequest(acao = "LIGAR"), ownerId)
        }
    }

    @Test
    fun `should reject command from user without link with 404`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(ownerId, deviceId)).thenReturn(null)

        assertThrows<ResourceNotFoundException> {
            service.comandar(deviceId, ComandoRequest(acao = "LIGAR"), ownerId)
        }
    }

    @Test
    fun `should reject invalid action with 400`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(ownerId, deviceId)).thenReturn(ownerLink)

        assertThrows<RequisicaoInvalidaException> {
            service.comandar(deviceId, ComandoRequest(acao = "FLUTUAR"), ownerId)
        }
    }

    @Test
    fun `should reject command when mqtt is unavailable`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(ownerId, deviceId)).thenReturn(ownerLink)
        `when`(
            mqttClientService.publish(
                anyString() ?: "",
                any() ?: Any(),
                any() ?: QualityOfService.AT_LEAST_ONCE,
                anyBoolean()
            )
        ).thenReturn(false)

        assertThrows<ServicoIndisponivelException> {
            service.comandar(deviceId, ComandoRequest(acao = "CANCELAR"), ownerId)
        }
    }

    @Test
    fun `diagnostico retorna dados persistidos para o proprietario`() {
        device.online = true
        device.rssi = -62
        device.uptimeS = 3600
        device.firmware = "1.2.3"
        device.ultimoVisto = Instant.parse("2026-11-09T10:00:00Z")
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(ownerId, deviceId)).thenReturn(ownerLink)
        val erro = EventoPreparo(
            id = UUID.randomUUID(),
            eventoId = "evt-err-1",
            dispositivo = device,
            tipo = "PREPARO",
            resultado = "ERRO",
            origem = "APP",
            duracaoS = 0,
            timestamp = Instant.parse("2026-11-09T09:59:00Z"),
            detalheErro = "boiler timeout"
        )
        `when`(
            eventoPreparoRepository.findEventosComFiltro(
                eq(deviceId) ?: deviceId,
                eq("ERRO") ?: "ERRO",
                nullable(Instant::class.java),
                nullable(Instant::class.java),
                any(PageRequest::class.java) ?: PageRequest.of(0, 5)
            )
        ).thenReturn(PageImpl(listOf(erro)))

        val res = service.obterDiagnostico(deviceId, ownerId)

        assertEquals(deviceId, res.dispositivoId)
        assertTrue(res.online)
        assertEquals(-62, res.rssi)
        assertEquals(3600L, res.uptimeS)
        assertEquals("1.2.3", res.firmware)
        assertEquals("DESLIGADO", res.estado)
        assertNull(res.estadoRele)
        assertEquals(1, res.errosRecentes.size)
        assertEquals("boiler timeout", res.errosRecentes[0].detalheErro)
    }

    @Test
    fun `diagnostico rejeita convidado`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(guestId, deviceId)).thenReturn(guestLink)

        assertThrows<BadCredentialsException> {
            service.obterDiagnostico(deviceId, guestId)
        }
    }

    @Test
    fun `diagnostico rejeita usuario sem vinculo`() {
        `when`(usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(ownerId, deviceId)).thenReturn(null)

        assertThrows<ResourceNotFoundException> {
            service.obterDiagnostico(deviceId, ownerId)
        }
    }
}
