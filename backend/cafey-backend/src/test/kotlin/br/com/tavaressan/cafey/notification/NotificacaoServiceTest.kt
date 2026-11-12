package br.com.tavaressan.cafey.notification

import br.com.tavaressan.cafey.device.Dispositivo
import br.com.tavaressan.cafey.device.DispositivoRepository
import br.com.tavaressan.cafey.device.UsuarioDispositivoRepository
import br.com.tavaressan.cafey.user.Usuario
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificacaoServiceTest {

    @Mock private lateinit var dispositivoRepository: DispositivoRepository
    @Mock private lateinit var usuarioDispositivoRepository: UsuarioDispositivoRepository
    @Mock private lateinit var registroPushRepository: RegistroPushRepository
    @Mock private lateinit var registroPushService: RegistroPushService

    private val agora = Instant.parse("2026-11-12T10:00:00Z")
    private val clock = Clock.fixed(agora, ZoneOffset.UTC)
    private val dispositivoId = UUID.randomUUID()
    private val dono = Usuario(id = UUID.randomUUID(), nome = "Dona", email = "dona@cafey.com", senhaHash = "x")
    private val convidado = Usuario(id = UUID.randomUUID(), nome = "Convidado", email = "conv@cafey.com", senhaHash = "x")
    private val pausas = mutableListOf<Long>()

    private lateinit var android: CanalFalso
    private lateinit var web: CanalFalso
    private lateinit var regDono: RegistroPush
    private lateinit var regConvidado: RegistroPush

    class CanalFalso(override val plataforma: PlataformaPush, override val habilitado: Boolean = true) : CanalPush {
        val enviados = mutableListOf<Pair<RegistroPush, MensagemPush>>()
        val respostas = ArrayDeque<ResultadoEnvio>()
        override fun enviar(registro: RegistroPush, mensagem: MensagemPush): ResultadoEnvio {
            enviados += registro to mensagem
            return respostas.removeFirstOrNull() ?: ResultadoEnvio.ENTREGUE
        }
    }

    private fun servico(vararg canais: CanalPush) = NotificacaoService(
        dispositivoRepository, usuarioDispositivoRepository, registroPushRepository, registroPushService,
        canais.toList(), NotificacaoProperties(), clock
    ) { pausas += it }

    @BeforeEach
    fun setUp() {
        android = CanalFalso(PlataformaPush.ANDROID)
        web = CanalFalso(PlataformaPush.WEB)
        regDono = RegistroPush(id = UUID.randomUUID(), usuario = dono, plataforma = PlataformaPush.ANDROID, destino = "tok-dono")
        regConvidado = RegistroPush(id = UUID.randomUUID(), usuario = convidado, plataforma = PlataformaPush.WEB, destino = "https://fcm.googleapis.com/x")
        `when`(dispositivoRepository.findById(dispositivoId))
            .thenReturn(Optional.of(Dispositivo(id = dispositivoId, nome = "Cafeteira da Cozinha", online = true)))
        `when`(usuarioDispositivoRepository.findUsuarioIdsByDispositivoId(dispositivoId)).thenReturn(listOf(dono.id!!, convidado.id!!))
        `when`(registroPushRepository.findByUsuarioIds(listOf(dono.id!!, convidado.id!!))).thenReturn(listOf(regDono, regConvidado))
    }

    private fun evento(resultado: String, ts: Instant = agora, detalhe: String? = null) =
        EventoPreparoRegistradoEvent(dispositivoId, "evt-1", resultado, ts, detalhe)

    @Test
    fun `cafe pronto chega ao dono e ao convidado com o texto do esboco`() {
        servico(android, web).notificarEventoPreparo(evento("CONCLUIDO"))

        val mensagem = android.enviados.single().second
        assertEquals(TipoNotificacao.CAFE_PRONTO, mensagem.tipo)
        assertEquals("Cafeteira da Cozinha", mensagem.titulo)
        assertEquals("Seu café está pronto! Enjoy!", mensagem.corpo)
        assertEquals("evt-1", mensagem.eventoId)
        assertEquals(regDono, android.enviados.single().first)
        assertEquals(regConvidado, web.enviados.single().first)
    }

    @Test
    fun `falha inclui o detalhe resumido`() {
        servico(android, web).notificarEventoPreparo(evento("ERRO", detalhe = "  timeout do rele  "))

        assertEquals("Não foi possível preparar o café: timeout do rele", android.enviados.single().second.corpo)
        assertEquals(TipoNotificacao.FALHA_PREPARO, android.enviados.single().second.tipo)
    }

    @Test
    fun `evento antigo da fila do firmware nao notifica`() {
        servico(android, web).notificarEventoPreparo(evento("CONCLUIDO", ts = agora.minus(Duration.ofMinutes(20))))

        assertTrue(android.enviados.isEmpty() && web.enviados.isEmpty())
    }

    @Test
    fun `resultado sem notificacao e ignorado`() {
        servico(android, web).notificarEventoPreparo(evento("CANCELADO"))

        assertTrue(android.enviados.isEmpty() && web.enviados.isEmpty())
    }

    @Test
    fun `destino invalido e removido sem retentativa`() {
        android.respostas += ResultadoEnvio.DESTINO_INVALIDO

        servico(android, web).notificarEventoPreparo(evento("CONCLUIDO"))

        assertEquals(1, android.enviados.size)
        verify(registroPushService).removerInvalido(regDono.id!!)
        assertTrue(pausas.isEmpty())
    }

    @Test
    fun `falha transitoria tenta de novo com backoff e para no sucesso`() {
        android.respostas += listOf(ResultadoEnvio.FALHA_TRANSITORIA, ResultadoEnvio.FALHA_TRANSITORIA, ResultadoEnvio.ENTREGUE)

        servico(android, web).notificarEventoPreparo(evento("CONCLUIDO"))

        assertEquals(3, android.enviados.size)
        assertEquals(listOf(1000L, 4000L), pausas)
        verify(registroPushService, never()).removerInvalido(any() ?: UUID.randomUUID())
    }

    @Test
    fun `excecao do canal conta como falha transitoria e nao propaga`() {
        val explosivo = object : CanalPush {
            override val plataforma = PlataformaPush.ANDROID
            override val habilitado = true
            var chamadas = 0
            override fun enviar(registro: RegistroPush, mensagem: MensagemPush): ResultadoEnvio {
                chamadas++
                throw IllegalStateException("rede caiu")
            }
        }

        servico(explosivo, web).notificarEventoPreparo(evento("CONCLUIDO"))

        assertEquals(3, explosivo.chamadas)
        assertEquals(1, web.enviados.size)
    }

    @Test
    fun `canal desligado nao e chamado`() {
        val desligado = CanalFalso(PlataformaPush.ANDROID, habilitado = false)

        servico(desligado, web).notificarEventoPreparo(evento("CONCLUIDO"))

        assertTrue(desligado.enviados.isEmpty())
        assertEquals(1, web.enviados.size)
    }

    @Test
    fun `offline avisa so se continuar offline sem sinal novo`() {
        val queda = agora.minusSeconds(180)
        val disp = Dispositivo(id = dispositivoId, nome = "Cafeteira da Cozinha", online = false, ultimoVisto = queda)
        `when`(dispositivoRepository.findById(dispositivoId)).thenReturn(Optional.of(disp))
        val s = servico(android, web)

        s.verificarOffline(dispositivoId, queda)
        assertEquals(TipoNotificacao.DISPOSITIVO_OFFLINE, android.enviados.single().second.tipo)

        disp.ultimoVisto = queda.plusSeconds(30)
        s.verificarOffline(dispositivoId, queda)
        disp.online = true
        disp.ultimoVisto = queda
        s.verificarOffline(dispositivoId, queda)
        assertEquals(1, android.enviados.size)
    }

    @Test
    fun `preparo iniciado usa o canal de status`() {
        servico(android, web).notificarPreparoIniciado(dispositivoId)

        val mensagem = android.enviados.single().second
        assertEquals(TipoNotificacao.PREPARO_INICIADO, mensagem.tipo)
        assertEquals("status", mensagem.tipo.canalAndroid)
        assertEquals("PREPARO_INICIADO:$dispositivoId", mensagem.tag)
    }
}
