package br.com.tavaressan.cafey.notification.fcm

import br.com.tavaressan.cafey.notification.MensagemPush
import br.com.tavaressan.cafey.notification.NotificacaoProperties
import br.com.tavaressan.cafey.notification.PlataformaPush
import br.com.tavaressan.cafey.notification.RegistroPush
import br.com.tavaressan.cafey.notification.ResultadoEnvio
import br.com.tavaressan.cafey.notification.TipoNotificacao
import br.com.tavaressan.cafey.user.Usuario
import tools.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.http.HttpClient
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.time.Duration
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Testes do canal FCM (#362) com servidor HTTP local (com.sun.net.httpserver.HttpServer).
 * Nenhum mock do HttpClient — as requisições são feitas de verdade contra o servidor local.
 */
class FcmCanalPushTest {

    private lateinit var server: HttpServer
    private lateinit var tokenHandler: MutableList<Pair<Int, String>>   // status → body
    private lateinit var fcmHandler: MutableList<Pair<Int, String>>      // status → body
    private lateinit var tokenRequests: MutableList<String>              // corpos recebidos
    private lateinit var fcmRequests: MutableList<String>                // corpos recebidos

    private lateinit var credenciaisPath: String
    private lateinit var canal: FcmCanalPush
    private lateinit var registro: RegistroPush
    private val mapper = ObjectMapper()

    // Par RSA gerado uma vez por suite de teste
    companion object {
        private val rsaKeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.genKeyPair()
        private val privateKeyPem = run {
            val b64 = Base64.getEncoder().encodeToString(rsaKeyPair.private.encoded)
            "-----BEGIN PRIVATE KEY-----\n${b64.chunked(64).joinToString("\n")}\n-----END PRIVATE KEY-----"
        }
    }

    @BeforeEach
    fun setUp() {
        tokenRequests = mutableListOf()
        fcmRequests = mutableListOf()
        tokenHandler = mutableListOf()
        fcmHandler = mutableListOf()

        server = HttpServer.create(InetSocketAddress(0), 0)
        val port = server.address.port

        server.createContext("/token") { exchange ->
            tokenRequests += exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            val (status, body) = tokenHandler.removeFirstOrNull()
                ?: (200 to """{"access_token":"test-token","expires_in":3600}""")
            exchange.sendResponseHeaders(status, body.length.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }

        server.createContext("/fcm") { exchange ->
            fcmRequests += exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            val (status, body) = fcmHandler.removeFirstOrNull()
                ?: (200 to """{"name":"projects/test/messages/123"}""")
            exchange.sendResponseHeaders(status, body.length.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }

        server.executor = null
        server.start()

        val serviceAccountJson = mapper.writeValueAsString(
            mapOf(
                "project_id" to "test-project",
                "client_email" to "test@test-project.iam.gserviceaccount.com",
                "private_key" to privateKeyPem,
                "token_uri" to "http://localhost:$port/token"
            )
        )

        val tmpFile = Files.createTempFile("fcm-test-", ".json")
        Files.writeString(tmpFile, serviceAccountJson)
        credenciaisPath = tmpFile.toString()

        val properties = NotificacaoProperties(
            fcmCredenciaisPath = credenciaisPath,
            fcmBaseUrl = "http://localhost:$port/fcm/dummy"
        )

        // Sobrescrevemos o fcmBaseUrl para apontar para o contexto correto
        val props = NotificacaoProperties(
            fcmCredenciaisPath = credenciaisPath,
            fcmBaseUrl = "http://localhost:$port"
        )
        // O canal monta: {fcmBaseUrl}/v1/projects/{projectId}/messages:send
        // Nossa URL de envio será: http://localhost:{port}/fcm/v1/projects/test-project/messages:send
        // Então precisamos de um contexto /fcm/v1/projects/test-project/messages:send
        server.createContext("/fcm/v1") { exchange ->
            fcmRequests += exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            val (status, body) = fcmHandler.removeFirstOrNull()
                ?: (200 to """{"name":"projects/test/messages/123"}""")
            exchange.sendResponseHeaders(status, body.length.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }

        val finalProps = NotificacaoProperties(
            fcmCredenciaisPath = credenciaisPath,
            fcmBaseUrl = "http://localhost:$port/fcm"
        )

        val httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build()

        canal = FcmCanalPush(finalProps, httpClient, mapper)

        val usuario = Usuario(id = UUID.randomUUID(), nome = "Usuário Teste", email = "teste@cafey.com", senhaHash = "x")
        registro = RegistroPush(
            id = UUID.randomUUID(),
            usuario = usuario,
            plataforma = PlataformaPush.ANDROID,
            destino = "fcm-token-de-teste"
        )
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
        Files.deleteIfExists(java.nio.file.Paths.get(credenciaisPath))
    }

    // ─── Testes de JWT ────────────────────────────────────────────────────────

    @Test
    fun `JWT enviado ao token_uri tem claims corretos e assinatura valida`() {
        val mensagem = mensagemTeste()
        canal.enviar(registro, mensagem)

        assertTrue(tokenRequests.isNotEmpty(), "Deve ter feito requisição ao token_uri")
        val body = tokenRequests.first()
        assertTrue(body.contains("grant_type="), "Deve conter grant_type")
        assertTrue(body.contains("urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer"), "grant_type deve ser jwt-bearer")
        assertTrue(body.contains("assertion="), "Deve conter assertion (JWT)")

        // Extrai o JWT e verifica claims e assinatura
        val assertion = body.substringAfter("assertion=").let {
            java.net.URLDecoder.decode(it, Charsets.UTF_8)
        }
        val partes = assertion.split(".")
        assertEquals(3, partes.size, "JWT deve ter 3 partes")

        val headerJson = String(Base64.getUrlDecoder().decode(padB64(partes[0])))
        assertTrue(headerJson.contains("\"alg\":\"RS256\""), "Header deve conter alg RS256")
        assertTrue(headerJson.contains("\"typ\":\"JWT\""), "Header deve conter typ JWT")

        val payloadJson = String(Base64.getUrlDecoder().decode(padB64(partes[1])))
        assertTrue(payloadJson.contains("test@test-project.iam.gserviceaccount.com"), "iss deve ser o client_email")
        assertTrue(payloadJson.contains("firebase.messaging"), "scope deve conter firebase.messaging")
        assertTrue(payloadJson.contains("/token"), "aud deve ser o token_uri")
        assertTrue(payloadJson.contains("\"iat\":"), "Deve conter iat")
        assertTrue(payloadJson.contains("\"exp\":"), "Deve conter exp")

        // Valida assinatura RS256 com a chave pública do par gerado
        val sig = Base64.getUrlDecoder().decode(padB64(partes[2]))
        val signing = "${partes[0]}.${partes[1]}"
        val javaSignature = java.security.Signature.getInstance("SHA256withRSA").apply {
            initVerify(rsaKeyPair.public)
            update(signing.toByteArray())
        }
        assertTrue(javaSignature.verify(sig), "Assinatura RS256 deve ser válida")
    }

    // ─── Testes de corpo da mensagem ──────────────────────────────────────────

    @Test
    fun `corpo da mensagem tem canal android prioridade ttl tag e data`() {
        canal.enviar(registro, mensagemTeste())

        assertTrue(fcmRequests.isNotEmpty(), "Deve ter feito requisição ao FCM")
        val tree = mapper.readTree(fcmRequests.first())
        val message = tree.path("message")

        // token FCM
        assertEquals("fcm-token-de-teste", message.path("token").asText())

        // notification
        assertEquals("Caféy", message.path("notification").path("title").asText())
        assertEquals("Teste", message.path("notification").path("body").asText())

        // android
        val android = message.path("android")
        // TESTE não é urgente
        assertEquals("NORMAL", android.path("priority").asText())
        assertEquals("${TipoNotificacao.TESTE.ttl.seconds}s", android.path("ttl").asText())
        val notif = android.path("notification")
        assertEquals(TipoNotificacao.TESTE.canalAndroid, notif.path("channel_id").asText())
        assertTrue(notif.path("tag").asText().contains("TESTE"), "tag deve conter o tipo")

        // data
        val data = message.path("data")
        assertEquals("TESTE", data.path("tipo").asText())
    }

    @Test
    fun `mensagem urgente usa prioridade HIGH`() {
        val mensagem = MensagemPush(TipoNotificacao.CAFE_PRONTO, "Título", "Corpo")
        canal.enviar(registro, mensagem)

        val tree = mapper.readTree(fcmRequests.first())
        assertEquals("HIGH", tree.path("message").path("android").path("priority").asText())
    }

    @Test
    fun `mensagem nao urgente usa prioridade NORMAL`() {
        canal.enviar(registro, mensagemTeste())

        val tree = mapper.readTree(fcmRequests.first())
        assertEquals("NORMAL", tree.path("message").path("android").path("priority").asText())
    }

    // ─── Testes de mapeamento de resposta ─────────────────────────────────────

    @Test
    fun `200 retorna ENTREGUE`() {
        val resultado = canal.enviar(registro, mensagemTeste())
        assertEquals(ResultadoEnvio.ENTREGUE, resultado)
    }

    @Test
    fun `404 retorna DESTINO_INVALIDO`() {
        fcmHandler += 404 to """{"error":{"code":404,"message":"Requested entity was not found.","status":"NOT_FOUND"}}"""
        val resultado = canal.enviar(registro, mensagemTeste())
        assertEquals(ResultadoEnvio.DESTINO_INVALIDO, resultado)
    }

    @Test
    fun `UNREGISTERED no details retorna DESTINO_INVALIDO`() {
        fcmHandler += 400 to """{"error":{"code":400,"status":"INVALID_ARGUMENT","details":[{"errorCode":"UNREGISTERED"}]}}"""
        val resultado = canal.enviar(registro, mensagemTeste())
        assertEquals(ResultadoEnvio.DESTINO_INVALIDO, resultado)
    }

    @Test
    fun `403 SENDER_ID_MISMATCH retorna DESTINO_INVALIDO`() {
        fcmHandler += 403 to """{"error":{"code":403,"details":[{"errorCode":"SENDER_ID_MISMATCH"}]}}"""
        val resultado = canal.enviar(registro, mensagemTeste())
        assertEquals(ResultadoEnvio.DESTINO_INVALIDO, resultado)
    }

    @Test
    fun `400 INVALID_ARGUMENT retorna FALHA_PERMANENTE`() {
        fcmHandler += 400 to """{"error":{"code":400,"status":"INVALID_ARGUMENT","details":[{"errorCode":"INVALID_ARGUMENT"}]}}"""
        val resultado = canal.enviar(registro, mensagemTeste())
        assertEquals(ResultadoEnvio.FALHA_PERMANENTE, resultado)
    }

    @Test
    fun `429 retorna FALHA_TRANSITORIA`() {
        fcmHandler += 429 to """{"error":{"code":429,"status":"RESOURCE_EXHAUSTED"}}"""
        val resultado = canal.enviar(registro, mensagemTeste())
        assertEquals(ResultadoEnvio.FALHA_TRANSITORIA, resultado)
    }

    @Test
    fun `500 retorna FALHA_TRANSITORIA`() {
        fcmHandler += 500 to """{"error":{"code":500,"status":"INTERNAL"}}"""
        val resultado = canal.enviar(registro, mensagemTeste())
        assertEquals(ResultadoEnvio.FALHA_TRANSITORIA, resultado)
    }

    @Test
    fun `503 retorna FALHA_TRANSITORIA`() {
        fcmHandler += 503 to """{"error":{"code":503,"status":"UNAVAILABLE"}}"""
        val resultado = canal.enviar(registro, mensagemTeste())
        assertEquals(ResultadoEnvio.FALHA_TRANSITORIA, resultado)
    }

    // ─── Testes de cache e renovação de token ─────────────────────────────────

    @Test
    fun `token e reusado em dois envios sem solicitar novo`() {
        canal.enviar(registro, mensagemTeste())
        canal.enviar(registro, mensagemTeste())

        assertEquals(1, tokenRequests.size, "Apenas um pedido de token para dois envios")
    }

    @Test
    fun `401 invalida o token e forca novo pedido no proximo envio`() {
        // Primeiro envio retorna 401 do FCM
        fcmHandler += 401 to """{"error":{"code":401,"status":"UNAUTHENTICATED"}}"""
        val resultado1 = canal.enviar(registro, mensagemTeste())
        assertEquals(ResultadoEnvio.FALHA_TRANSITORIA, resultado1)
        assertEquals(1, tokenRequests.size, "Primeira requisição de token")

        // Segundo envio deve pedir novo token
        canal.enviar(registro, mensagemTeste())
        assertEquals(2, tokenRequests.size, "401 deve forçar novo pedido de token")
    }

    // ─── Testes de configuração ───────────────────────────────────────────────

    @Test
    fun `sem credencial configurada canal fica desligado e contexto Spring nao quebra`() {
        val props = NotificacaoProperties(fcmCredenciaisPath = "")
        val httpClient = HttpClient.newBuilder().build()
        val semCredencial = FcmCanalPush(props, httpClient, mapper)

        assertFalse(semCredencial.habilitado, "Canal sem credencial deve estar desligado")
        assertEquals(PlataformaPush.ANDROID, semCredencial.plataforma)
        // enviar em canal desligado não deve lançar exceção
        val resultado = semCredencial.enviar(registro, mensagemTeste())
        assertEquals(ResultadoEnvio.FALHA_PERMANENTE, resultado)
    }

    @Test
    fun `credencial inexistente desliga canal sem lancar excecao`() {
        val props = NotificacaoProperties(fcmCredenciaisPath = "/tmp/nao-existe-${UUID.randomUUID()}.json")
        val httpClient = HttpClient.newBuilder().build()
        val semCredencial = FcmCanalPush(props, httpClient, mapper)

        assertFalse(semCredencial.habilitado)
    }

    @Test
    fun `credencial com json invalido desliga canal sem lancar excecao`() {
        val tmpFile = Files.createTempFile("fcm-broken-", ".json")
        try {
            Files.writeString(tmpFile, """{"project_id":"only-this"}""")
            val props = NotificacaoProperties(fcmCredenciaisPath = tmpFile.toString())
            val httpClient = HttpClient.newBuilder().build()
            val canal = FcmCanalPush(props, httpClient, mapper)
            assertFalse(canal.habilitado, "JSON incompleto deve desligar o canal")
        } finally {
            Files.deleteIfExists(tmpFile)
        }
    }

    // ─── Utilitários ──────────────────────────────────────────────────────────

    private fun mensagemTeste() = MensagemPush(
        tipo = TipoNotificacao.TESTE,
        titulo = "Caféy",
        corpo = "Teste"
    )

    private fun padB64(s: String): String {
        val pad = (4 - s.length % 4) % 4
        return s + "=".repeat(pad)
    }
}
