package br.com.tavaressan.cafey.notification.webpush

import br.com.tavaressan.cafey.notification.MensagemPush
import br.com.tavaressan.cafey.notification.NotificacaoProperties
import br.com.tavaressan.cafey.notification.PlataformaPush
import br.com.tavaressan.cafey.notification.RegistroPush
import br.com.tavaressan.cafey.notification.RegistroPushService
import br.com.tavaressan.cafey.notification.ResultadoEnvio
import br.com.tavaressan.cafey.notification.TipoNotificacao
import br.com.tavaressan.cafey.user.Usuario
import br.com.tavaressan.cafey.user.UsuarioRepository
import br.com.tavaressan.cafey.notification.RegistroPushRepository
import tools.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import br.com.tavaressan.cafey.mqtt.MqttClientService
import java.net.InetSocketAddress
import java.net.http.HttpClient
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

class WebPushCanalPushTest {

    private val mapper = ObjectMapper()

    // --- Vetor RFC 8291 Apêndice A ---
    private val authSecret = b64u("BTBZMqHH6r4Tts7J_aSIgg")
    private val uaPublic = b64u(
        "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4"
    )
    private val uaPrivate = b64u("q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94")
    private val asPublic = b64u(
        "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8"
    )
    private val asPrivate = b64u("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw")
    private val salt = b64u("DGv6ra1nlYgDCS1FRnbzlw")
    private val corpoEsperado = b64u(
        "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27ml" +
            "mlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPT" +
            "pK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN"
    )

    @Test
    fun `vetor RFC 8291 apendice A reproduz corpo cifrado byte a byte`() {
        val parEfemero = par(asPublic, asPrivate)
        val plaintext = "When I grow up, I want to be a watermelon".toByteArray(StandardCharsets.UTF_8)
        val corpo = WebPushCriptografia.criptografar(
            WebPushCriptografia.EntradaCriptografia(
                uaPublic = uaPublic,
                authSecret = authSecret,
                plaintext = plaintext,
                salt = salt,
                parEfemero = parEfemero
            )
        )
        assertArrayEquals(corpoEsperado, corpo)
    }

    @Test
    fun `round-trip decifra com a chave privada do navegador`() {
        val plaintext = mapper.writeValueAsBytes(mapOf("tipo" to "TESTE", "titulo" to "Caféy", "corpo" to "Oi"))
        val corpo = WebPushCriptografia.criptografar(
            WebPushCriptografia.EntradaCriptografia(
                uaPublic = uaPublic,
                authSecret = authSecret,
                plaintext = plaintext
            )
        )
        val decifrado = WebPushCriptografia.decifrar(
            WebPushChaves.privadaDeBytes(uaPrivate), uaPublic, authSecret, corpo
        )
        assertArrayEquals(plaintext, decifrado)
    }

    @Test
    fun `JWT VAPID tem claims corretas e assinatura verificavel pela publica`() {
        val parVapid = WebPushCriptografia.gerarParEfemero()
        val clock = Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneOffset.UTC)
        val vapid = WebPushVapid(parVapid, "mailto:ops@cafey.app", clock)
        val endpoint = "https://fcm.googleapis.com/fcm/send/abc"
        val exp = clock.instant().plusSeconds(12 * 3600).epochSecond
        val jwt = vapid.gerarJwt(WebPushVapid.origem(endpoint), exp)
        val partes = jwt.split(".")
        assertEquals(3, partes.size)
        val header = String(b64u(partes[0]))
        val payload = String(b64u(partes[1]))
        assertTrue(header.contains("\"alg\":\"ES256\""))
        assertTrue(header.contains("\"typ\":\"JWT\""))
        assertTrue(payload.contains("\"aud\":\"https://fcm.googleapis.com\""))
        assertTrue(payload.contains("\"exp\":$exp"))
        assertTrue(payload.contains("\"sub\":\"mailto:ops@cafey.app\""))

        val ver = Signature.getInstance("SHA256withECDSAinP1363Format")
        ver.initVerify(parVapid.public as ECPublicKey)
        ver.update("${partes[0]}.${partes[1]}".toByteArray(Charsets.US_ASCII))
        assertTrue(ver.verify(b64u(partes[2])), "assinatura ES256 (P-1363) deve ser válida")

        val authz = vapid.autorizacao(endpoint)
        assertTrue(authz.startsWith("vapid t="))
        assertTrue(authz.contains(", k=${vapid.publicaB64}"))
    }

    @Test
    fun `mapeamento de respostas com servidor local`() {
        val respostas = CopyOnWriteArrayList<Pair<Int, String>>()
        val recebidas = CopyOnWriteArrayList<Map<String, List<String>>>()
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { ex ->
            recebidas += ex.requestHeaders.mapValues { it.value.toList() }
            val (status, body) = respostas.removeAt(0)
            ex.sendResponseHeaders(status, body.toByteArray().size.toLong())
            ex.responseBody.use { it.write(body.toByteArray()) }
        }
        server.start()
        try {
            val props = NotificacaoProperties(
                vapidPublicKey = WebPushChaves.codificarBase64Url(asPublic),
                vapidPrivateKey = WebPushChaves.codificarBase64Url(asPrivate),
                vapidSubject = "mailto:ops@cafey.app",
                webpushPermitirHttp = true,
                webpushHostsPermitidos = listOf("localhost")
            )
            val httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
            val registroSvc = RegistroPushService(
                repoFake(), repoUsuarioFake(), props
            )
            val canal = WebPushCanalPush(props, httpClient, mapper, registroSvc)
            assertTrue(canal.habilitado)

            val endpoint = "http://localhost:${server.address.port}/push/abc"
            val registro = registro(endpoint)
            val mensagem = MensagemPush(TipoNotificacao.TESTE, "Caféy", "Teste", eventoId = "evt")

            respostas += 201 to ""
            assertEquals(ResultadoEnvio.ENTREGUE, canal.enviar(registro, mensagem))
            val headers = recebidas.last().mapKeys { it.key.lowercase() }
            assertEquals(listOf("aes128gcm"), headers["content-encoding"])
            assertEquals(listOf("application/octet-stream"), headers["content-type"])
            assertEquals(listOf(TipoNotificacao.TESTE.ttl.seconds.toString()), headers["ttl"])
            assertEquals(listOf("normal"), headers["urgency"])
            assertTrue(headers["authorization"]!!.first().startsWith("vapid t="))
            val topic = headers["topic"]!!.first()
            assertTrue(topic.length <= 32 && topic.matches(Regex("[A-Za-z0-9_-]+")))

            respostas += 410 to ""; assertEquals(ResultadoEnvio.DESTINO_INVALIDO, canal.enviar(registro, mensagem))
            respostas += 404 to ""; assertEquals(ResultadoEnvio.DESTINO_INVALIDO, canal.enviar(registro, mensagem))
            respostas += 403 to ""; assertEquals(ResultadoEnvio.FALHA_PERMANENTE, canal.enviar(registro, mensagem))
            respostas += 400 to ""; assertEquals(ResultadoEnvio.FALHA_PERMANENTE, canal.enviar(registro, mensagem))
            respostas += 413 to ""; assertEquals(ResultadoEnvio.FALHA_PERMANENTE, canal.enviar(registro, mensagem))
            respostas += 429 to ""; assertEquals(ResultadoEnvio.FALHA_TRANSITORIA, canal.enviar(registro, mensagem))
            respostas += 503 to ""; assertEquals(ResultadoEnvio.FALHA_TRANSITORIA, canal.enviar(registro, mensagem))
            respostas += 500 to ""; assertEquals(ResultadoEnvio.FALHA_TRANSITORIA, canal.enviar(registro, mensagem))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `sem chaves canal fica desligado`() {
        val props = NotificacaoProperties()
        val canal = WebPushCanalPush(props, HttpClient.newHttpClient(), mapper, registroSvc(props))
        assertFalse(canal.habilitado)
        assertEquals(PlataformaPush.WEB, canal.plataforma)
        val r = canal.enviar(registro("https://fcm.googleapis.com/x"), MensagemPush(TipoNotificacao.TESTE, "t", "c"))
        assertEquals(ResultadoEnvio.FALHA_PERMANENTE, r)
    }

    @Test
    fun `chave invalida ou inconsistente desliga o canal`() {
        val props = NotificacaoProperties(
            vapidPublicKey = "invalida",
            vapidPrivateKey = "invalida",
            vapidSubject = "mailto:ops@cafey.app"
        )
        val canal = WebPushCanalPush(props, HttpClient.newHttpClient(), mapper, registroSvc(props))
        assertFalse(canal.habilitado)
    }

    private fun registro(endpoint: String): RegistroPush {
        val usuario = Usuario(id = UUID.randomUUID(), nome = "U", email = "u@cafey.com", senhaHash = "x")
        return RegistroPush(
            id = UUID.randomUUID(), usuario = usuario, plataforma = PlataformaPush.WEB,
            destino = endpoint,
            chaveP256dh = WebPushChaves.codificarBase64Url(uaPublic),
            chaveAuth = WebPushChaves.codificarBase64Url(authSecret)
        )
    }

    private fun par(pub: ByteArray, priv: ByteArray): KeyPair =
        KeyPair(WebPushChaves.publicaDeBytes(pub), WebPushChaves.privadaDeBytes(priv))

    private fun registroSvc(props: NotificacaoProperties): RegistroPushService =
        RegistroPushService(repoFake(), repoUsuarioFake(), props)

    @Suppress("UNCHECKED_CAST")
    private fun repoFake(): RegistroPushRepository =
        java.lang.reflect.Proxy.newProxyInstance(
            RegistroPushRepository::class.java.classLoader, arrayOf(RegistroPushRepository::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "hashCode" -> 0; "toString" -> "fake"; "equals" -> false; else -> null
            }
        } as RegistroPushRepository

    @Suppress("UNCHECKED_CAST")
    private fun repoUsuarioFake(): UsuarioRepository =
        java.lang.reflect.Proxy.newProxyInstance(
            UsuarioRepository::class.java.classLoader, arrayOf(UsuarioRepository::class.java)
        ) { _, method, _ ->
            when (method.name) { "hashCode" -> 0; "toString" -> "fake"; "equals" -> false; else -> null }
        } as UsuarioRepository

    private fun b64u(s: String): ByteArray = Base64.getUrlDecoder().decode(s)
}

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class WebPushContextoTest @Autowired constructor() {
    @MockitoBean private lateinit var mqttClientService: MqttClientService

    @Autowired private lateinit var canais: List<br.com.tavaressan.cafey.notification.CanalPush>

    @Test
    fun `sem chaves VAPID contexto Spring sobe e canal web fica desligado`() {
        val web = canais.filterIsInstance<WebPushCanalPush>()
        assertTrue(web.isNotEmpty(), "WebPushCanalPush deve estar registrado como bean")
        assertFalse(web.first().habilitado)
    }
}
