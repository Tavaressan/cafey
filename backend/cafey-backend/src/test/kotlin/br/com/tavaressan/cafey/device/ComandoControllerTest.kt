package br.com.tavaressan.cafey.device

import br.com.tavaressan.cafey.mqtt.MqttClientService
import br.com.tavaressan.cafey.security.JwtTokenService
import br.com.tavaressan.cafey.user.Usuario
import br.com.tavaressan.cafey.user.UsuarioRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import software.amazon.awssdk.crt.mqtt.QualityOfService
import java.util.UUID

/**
 * Testes de integração ponta a ponta (HTTP real via MockMvc, banco H2 real) para o
 * `POST /dispositivos/{id}/comando` (issue #141). O cliente MQTT é dublado (`@MockitoBean`)
 * para controlar o resultado da publicação sem depender de um broker real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ComandoControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val usuarioRepository: UsuarioRepository,
    private val dispositivoRepository: DispositivoRepository,
    private val usuarioDispositivoRepository: UsuarioDispositivoRepository,
    private val jwtTokenService: JwtTokenService
) {

    @MockitoBean
    private lateinit var mqttClientService: MqttClientService

    private lateinit var proprietario: Usuario
    private lateinit var estranho: Usuario
    private lateinit var dispositivo: Dispositivo

    @BeforeEach
    fun setUp() {
        proprietario = usuarioRepository.save(
            Usuario(nome = "Dona Cafeteira", email = "dona-${UUID.randomUUID()}@cafey.com", senhaHash = "hash")
        )
        estranho = usuarioRepository.save(
            Usuario(nome = "Sem Vinculo", email = "estranho-${UUID.randomUUID()}@cafey.com", senhaHash = "hash")
        )
        dispositivo = dispositivoRepository.save(
            Dispositivo(nome = "Cafeteira Comando", duracaoPreparoS = 300)
        )
        usuarioDispositivoRepository.save(
            UsuarioDispositivo(usuario = proprietario, dispositivo = dispositivo, papel = PapelDispositivo.PROPRIETARIO)
        )
    }

    @AfterEach
    fun tearDown() {
        usuarioDispositivoRepository.findByDispositivoId(dispositivo.id!!).forEach { usuarioDispositivoRepository.delete(it) }
        dispositivoRepository.delete(dispositivo)
        usuarioRepository.delete(proprietario)
        usuarioRepository.delete(estranho)
    }

    private fun bearerTokenPara(usuario: Usuario): String =
        "Bearer " + jwtTokenService.generateAccessToken(usuario.id!!, usuario.email)

    @Test
    fun `deve publicar comando ligar com sucesso`() {
        `when`(
            mqttClientService.publish(
                anyString() ?: "",
                any() ?: Any(),
                any() ?: QualityOfService.AT_LEAST_ONCE,
                anyBoolean()
            )
        ).thenReturn(true)

        mockMvc.perform(
            post("/dispositivos/${dispositivo.id}/comando")
                .header(HttpHeaders.AUTHORIZATION, bearerTokenPara(proprietario))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"acao":"LIGAR"}""")
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.acao").value("LIGAR"))
            .andExpect(jsonPath("$.comandoId").isNotEmpty)
    }

    @Test
    fun `deve recusar comando sem token com 401`() {
        mockMvc.perform(
            post("/dispositivos/${dispositivo.id}/comando")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"acao":"LIGAR"}""")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `deve recusar comando de usuario sem vinculo com 404`() {
        mockMvc.perform(
            post("/dispositivos/${dispositivo.id}/comando")
                .header(HttpHeaders.AUTHORIZATION, bearerTokenPara(estranho))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"acao":"LIGAR"}""")
        )
            // Mesmo 404 do dispositivo inexistente: sem vinculo, o usuario nao descobre que ele existe.
            .andExpect(status().isNotFound)
    }

    @Test
    fun `deve recusar comando para dispositivo inexistente com 404`() {
        mockMvc.perform(
            post("/dispositivos/${UUID.randomUUID()}/comando")
                .header(HttpHeaders.AUTHORIZATION, bearerTokenPara(proprietario))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"acao":"LIGAR"}""")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `deve recusar acao invalida com 400`() {
        mockMvc.perform(
            post("/dispositivos/${dispositivo.id}/comando")
                .header(HttpHeaders.AUTHORIZATION, bearerTokenPara(proprietario))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"acao":"VOAR"}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `deve recusar duracao acima do teto de seguranca com 400`() {
        mockMvc.perform(
            post("/dispositivos/${dispositivo.id}/comando")
                .header(HttpHeaders.AUTHORIZATION, bearerTokenPara(proprietario))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"acao":"LIGAR","duracaoS":2147483647}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `deve recusar comando quando mqtt esta indisponivel`() {
        `when`(
            mqttClientService.publish(
                anyString() ?: "",
                any() ?: Any(),
                any() ?: QualityOfService.AT_LEAST_ONCE,
                anyBoolean()
            )
        ).thenReturn(false)

        mockMvc.perform(
            post("/dispositivos/${dispositivo.id}/comando")
                .header(HttpHeaders.AUTHORIZATION, bearerTokenPara(proprietario))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"acao":"DESLIGAR"}""")
        )
            .andExpect(status().isServiceUnavailable)
    }
}
