package br.com.tavaressan.cafey.event

import br.com.tavaressan.cafey.device.Dispositivo
import br.com.tavaressan.cafey.device.DispositivoRepository
import br.com.tavaressan.cafey.device.PapelDispositivo
import br.com.tavaressan.cafey.device.UsuarioDispositivo
import br.com.tavaressan.cafey.device.UsuarioDispositivoRepository
import br.com.tavaressan.cafey.security.JwtTokenService
import br.com.tavaressan.cafey.user.Usuario
import br.com.tavaressan.cafey.user.UsuarioRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

/**
 * Testes HTTP (MockMvc, banco H2 real) da Bean Validation do lote de
 * `POST /dispositivos/{dispositivoId}/eventos/proxy-ble` (issue #215).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ProxyBleValidacaoControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val usuarioRepository: UsuarioRepository,
    private val dispositivoRepository: DispositivoRepository,
    private val usuarioDispositivoRepository: UsuarioDispositivoRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val jwtTokenService: JwtTokenService
) {

    private lateinit var proprietario: Usuario
    private lateinit var dispositivo: Dispositivo

    @BeforeEach
    fun setUp() {
        proprietario = usuarioRepository.save(
            Usuario(nome = "Dona Cafeteira", email = "dona-${UUID.randomUUID()}@cafey.com", senhaHash = "hash")
        )
        dispositivo = dispositivoRepository.save(Dispositivo(nome = "Cafeteira Proxy"))
        usuarioDispositivoRepository.save(
            UsuarioDispositivo(usuario = proprietario, dispositivo = dispositivo, papel = PapelDispositivo.PROPRIETARIO)
        )
    }

    @AfterEach
    fun tearDown() {
        // O H2 (ddl-auto create-drop) não herda o ON DELETE CASCADE da migration V5.
        jdbcTemplate.update("DELETE FROM eventos_preparo WHERE dispositivo_id = ?", dispositivo.id)
        usuarioDispositivoRepository.findByDispositivoId(dispositivo.id!!).forEach { usuarioDispositivoRepository.delete(it) }
        dispositivoRepository.delete(dispositivo)
        usuarioRepository.delete(proprietario)
    }

    private fun bearer(): String =
        "Bearer " + jwtTokenService.generateAccessToken(proprietario.id!!, proprietario.email)

    private fun eventoJson(eventoId: String = "evt-${UUID.randomUUID()}", resultado: String = "CANCELADO", origem: String = "APP") =
        """{"eventoId":"$eventoId","resultado":"$resultado","origem":"$origem","timestamp":"${Instant.now()}"}"""

    private fun enviar(vararg eventos: String): ResultActions = enviar(eventos.toList())

    private fun enviar(eventos: List<String>): ResultActions =
        mockMvc.perform(
            post("/dispositivos/${dispositivo.id}/eventos/proxy-ble")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"eventos":[${eventos.joinToString(",")}]}""")
        )

    @Test
    fun `deve responder 400 em problem details com lote vazio`() {
        enviar(emptyList())
            .andExpect(status().isBadRequest)
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("Validation failed: eventos: Lote de eventos não pode ser vazio"))
    }

    @Test
    fun `deve responder 400 com lote de 101 eventos`() {
        enviar(List(101) { eventoJson() })
            .andExpect(status().isBadRequest)
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("Validation failed: eventos: Lote de eventos deve ter no máximo 100 eventos"))
    }

    @Test
    fun `deve responder 201 com lote de 100 eventos`() {
        enviar(List(100) { eventoJson() })
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.length()").value(100))
    }

    @Test
    fun `deve responder 201 com lote valido`() {
        enviar(eventoJson(eventoId = "evt-ble-1", resultado = "CONCLUIDO"), eventoJson(eventoId = "evt-ble-2"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].eventoId").value("evt-ble-1"))
    }

    @Test
    fun `deve responder 400 com o campo do elemento quando eventoId esta em branco`() {
        enviar(eventoJson(), eventoJson(eventoId = " "))
            .andExpect(status().isBadRequest)
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("Validation failed: eventos[1].eventoId: ID do evento é obrigatório"))
    }

    @Test
    fun `deve responder 400 com o campo do elemento quando resultado ou origem estao em branco`() {
        enviar(eventoJson(resultado = ""), eventoJson(origem = " "))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("eventos[0].resultado: ")))
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("eventos[1].origem: ")))
    }
}
