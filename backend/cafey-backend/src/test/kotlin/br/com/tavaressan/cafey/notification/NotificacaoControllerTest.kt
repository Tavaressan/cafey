package br.com.tavaressan.cafey.notification

import br.com.tavaressan.cafey.mqtt.MqttClientService
import br.com.tavaressan.cafey.security.JwtTokenService
import br.com.tavaressan.cafey.user.Usuario
import br.com.tavaressan.cafey.user.UsuarioRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.Base64
import java.util.UUID

/** #360: registro de destinos de push via HTTP real (MockMvc + H2). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class NotificacaoControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val usuarioRepository: UsuarioRepository,
    private val registroPushRepository: RegistroPushRepository,
    private val jwtTokenService: JwtTokenService
) {
    @MockitoBean
    private lateinit var mqttClientService: MqttClientService

    private lateinit var ana: Usuario
    private lateinit var bia: Usuario

    private val p256dh = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(65).also { it[0] = 4 })
    private val auth = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(16) { 7 })

    @BeforeEach
    fun setUp() {
        ana = usuarioRepository.save(Usuario(nome = "Ana", email = "ana-${UUID.randomUUID()}@cafey.com", senhaHash = "h"))
        bia = usuarioRepository.save(Usuario(nome = "Bia", email = "bia-${UUID.randomUUID()}@cafey.com", senhaHash = "h"))
    }

    @AfterEach
    fun tearDown() {
        registroPushRepository.deleteAll()
        usuarioRepository.delete(ana)
        usuarioRepository.delete(bia)
    }

    private fun bearer(u: Usuario) = "Bearer " + jwtTokenService.generateAccessToken(u.id!!, u.email)

    private fun registrar(u: Usuario, corpo: String) = mockMvc.perform(
        post("/notificacoes/registros")
            .header(HttpHeaders.AUTHORIZATION, bearer(u))
            .contentType(MediaType.APPLICATION_JSON)
            .content(corpo)
    )

    @Test
    fun `registra token android e re-registro e idempotente`() {
        registrar(ana, """{"plataforma":"ANDROID","token":"tok-1"}""")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.plataforma").value("ANDROID"))
            .andExpect(jsonPath("$.id").isNotEmpty)
        registrar(ana, """{"plataforma":"ANDROID","token":"tok-1"}""").andExpect(status().isCreated)

        assertEquals(1, registroPushRepository.findByUsuarioIdOrderByUltimoUsoEmDesc(ana.id!!).size)
    }

    @Test
    fun `mesmo token registrado por outro usuario troca o dono`() {
        registrar(ana, """{"plataforma":"ANDROID","token":"tok-compartilhado"}""").andExpect(status().isCreated)
        registrar(bia, """{"plataforma":"ANDROID","token":"tok-compartilhado"}""").andExpect(status().isCreated)

        assertEquals(bia.id, registroPushRepository.findByDestino("tok-compartilhado")!!.usuario.id)
        assertEquals(0, registroPushRepository.findByUsuarioIdOrderByUltimoUsoEmDesc(ana.id!!).size)
    }

    @Test
    fun `registra assinatura web de servico conhecido`() {
        registrar(ana, """{"plataforma":"WEB","endpoint":"https://fcm.googleapis.com/fcm/send/abc","p256dh":"$p256dh","auth":"$auth"}""")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.plataforma").value("WEB"))

        val salvo = registroPushRepository.findByDestino("https://fcm.googleapis.com/fcm/send/abc")!!
        assertEquals(p256dh, salvo.chaveP256dh)
    }

    @Test
    fun `recusa endpoint fora da lista de servicos de push`() {
        registrar(ana, """{"plataforma":"WEB","endpoint":"https://169.254.169.254/latest","p256dh":"$p256dh","auth":"$auth"}""")
            .andExpect(status().isBadRequest)
        registrar(ana, """{"plataforma":"WEB","endpoint":"http://fcm.googleapis.com/x","p256dh":"$p256dh","auth":"$auth"}""")
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `recusa campos incoerentes com a plataforma`() {
        registrar(ana, """{"plataforma":"ANDROID"}""").andExpect(status().isBadRequest)
        registrar(ana, """{"plataforma":"WEB","endpoint":"https://fcm.googleapis.com/x","p256dh":"AAAA","auth":"$auth"}""")
            .andExpect(status().isBadRequest)
        registrar(ana, """{"token":"x"}""").andExpect(status().isBadRequest)
    }

    @Test
    fun `remover por destino e idempotente e nao apaga registro de outro usuario`() {
        registrar(ana, """{"plataforma":"ANDROID","token":"tok-ana"}""").andExpect(status().isCreated)

        repeat(2) {
            mockMvc.perform(
                post("/notificacoes/registros/remover")
                    .header(HttpHeaders.AUTHORIZATION, bearer(bia))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"destino":"tok-ana"}""")
            ).andExpect(status().isNoContent)
        }
        assertEquals(ana.id, registroPushRepository.findByDestino("tok-ana")!!.usuario.id)

        mockMvc.perform(
            post("/notificacoes/registros/remover")
                .header(HttpHeaders.AUTHORIZATION, bearer(ana))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"destino":"tok-ana"}""")
        ).andExpect(status().isNoContent)
        assertNull(registroPushRepository.findByDestino("tok-ana"))
    }

    @Test
    fun `delete por id so do proprio usuario`() {
        registrar(ana, """{"plataforma":"ANDROID","token":"tok-del"}""").andExpect(status().isCreated)
        val id = registroPushRepository.findByDestino("tok-del")!!.id

        mockMvc.perform(delete("/notificacoes/registros/$id").header(HttpHeaders.AUTHORIZATION, bearer(bia)))
            .andExpect(status().isNotFound)
        mockMvc.perform(delete("/notificacoes/registros/$id").header(HttpHeaders.AUTHORIZATION, bearer(ana)))
            .andExpect(status().isNoContent)
        assertNull(registroPushRepository.findByDestino("tok-del"))
    }

    @Test
    fun `limite de registros por usuario descarta os mais antigos`() {
        repeat(12) { i ->
            registrar(ana, """{"plataforma":"ANDROID","token":"tok-$i"}""").andExpect(status().isCreated)
        }
        mockMvc.perform(get("/notificacoes/registros").header(HttpHeaders.AUTHORIZATION, bearer(ana)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(10))
        assertNull(registroPushRepository.findByDestino("tok-0"))
    }

    @Test
    fun `vapid sem configuracao responde 503 e rotas exigem token`() {
        mockMvc.perform(get("/notificacoes/vapid-public-key").header(HttpHeaders.AUTHORIZATION, bearer(ana)))
            .andExpect(status().isServiceUnavailable)
        mockMvc.perform(get("/notificacoes/registros")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `teste sem canais configurados responde com zero entregas`() {
        registrar(ana, """{"plataforma":"ANDROID","token":"tok-teste"}""").andExpect(status().isCreated)

        mockMvc.perform(post("/notificacoes/teste").header(HttpHeaders.AUTHORIZATION, bearer(ana)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.destinos").value(1))
            .andExpect(jsonPath("$.entregues").value(0))
    }
}
