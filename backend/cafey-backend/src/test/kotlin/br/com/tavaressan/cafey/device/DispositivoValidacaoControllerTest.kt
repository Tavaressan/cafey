package br.com.tavaressan.cafey.device

import br.com.tavaressan.cafey.security.JwtTokenService
import br.com.tavaressan.cafey.user.Usuario
import br.com.tavaressan.cafey.user.UsuarioRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * Testes HTTP (MockMvc, banco H2 real) da Bean Validation de `POST /dispositivos` e `PUT /dispositivos/{id}`
 * (issue #215). Os casos de 200 do PUT mantêm timezone e duração iguais aos do dispositivo, para não
 * disparar a republicação dos agendamentos (MQTT).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class DispositivoValidacaoControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val usuarioRepository: UsuarioRepository,
    private val dispositivoRepository: DispositivoRepository,
    private val usuarioDispositivoRepository: UsuarioDispositivoRepository,
    private val jwtTokenService: JwtTokenService
) {

    private lateinit var proprietario: Usuario
    private lateinit var dispositivo: Dispositivo

    @BeforeEach
    fun setUp() {
        proprietario = usuarioRepository.save(
            Usuario(nome = "Dona Cafeteira", email = "dona-${UUID.randomUUID()}@cafey.com", senhaHash = "hash")
        )
        dispositivo = dispositivoRepository.save(Dispositivo(nome = "Cafeteira Cozinha", duracaoPreparoS = 300))
        usuarioDispositivoRepository.save(
            UsuarioDispositivo(usuario = proprietario, dispositivo = dispositivo, papel = PapelDispositivo.PROPRIETARIO)
        )
    }

    @AfterEach
    fun tearDown() {
        // Inclui os dispositivos criados via POST durante o teste.
        usuarioDispositivoRepository.findByUsuarioId(proprietario.id!!).forEach {
            usuarioDispositivoRepository.delete(it)
            dispositivoRepository.delete(it.dispositivo)
        }
        usuarioRepository.delete(proprietario)
    }

    private fun bearer(): String =
        "Bearer " + jwtTokenService.generateAccessToken(proprietario.id!!, proprietario.email)

    private fun atualizar(corpo: String): ResultActions =
        mockMvc.perform(
            put("/dispositivos/${dispositivo.id}")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo)
        )

    private fun criar(corpo: String): ResultActions =
        mockMvc.perform(
            post("/dispositivos")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo)
        )

    private fun ResultActions.expectValidationFailed(campo: String, mensagem: String): ResultActions =
        andExpect(status().isBadRequest)
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("Validation failed: $campo: $mensagem"))

    // --- PUT ---

    @Test
    fun `PUT deve responder 400 com duracao abaixo de 30`() {
        atualizar("""{"duracaoPreparoS":10}""")
            .expectValidationFailed("duracaoPreparoS", "Duração de preparo deve ter no mínimo 30s")
    }

    @Test
    fun `PUT deve responder 400 com duracao acima de 900`() {
        atualizar("""{"duracaoPreparoS":901}""")
            .expectValidationFailed("duracaoPreparoS", "Duração de preparo deve ter no máximo 900s")
    }

    @Test
    fun `PUT deve responder 200 com duracao dentro da faixa`() {
        atualizar("""{"duracaoPreparoS":300}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.duracaoPreparoS").value(300))
    }

    @Test
    fun `PUT deve responder 400 com limiar zero`() {
        atualizar("""{"limiarDescalcificacao":0}""")
            .expectValidationFailed("limiarDescalcificacao", "Limiar de descalcificação deve ser maior que zero")
    }

    @Test
    fun `PUT deve responder 400 com nome so de espacos`() {
        atualizar("""{"nome":"   "}""")
            .expectValidationFailed("nome", "Nome do dispositivo não pode ser vazio")
    }

    @Test
    fun `PUT deve responder 400 com nome de 256 caracteres`() {
        atualizar("""{"nome":"${"a".repeat(256)}"}""")
            .expectValidationFailed("nome", "Nome do dispositivo deve ter no máximo 255 caracteres")
    }

    @Test
    fun `PUT deve aceitar nome com quebra de linha`() {
        atualizar("""{"nome":"Cafeteira\nCozinha"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nome").value("Cafeteira\nCozinha"))
    }

    @Test
    fun `PUT sem nome deve responder 200 e manter o nome`() {
        atualizar("""{"limiarDescalcificacao":250}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nome").value("Cafeteira Cozinha"))
            .andExpect(jsonPath("$.limiarDescalcificacao").value(250))
    }

    @Test
    fun `PUT deve responder 400 com timezone de 51 caracteres`() {
        atualizar("""{"timezone":"${"a".repeat(51)}"}""")
            .expectValidationFailed("timezone", "Timezone deve ter no máximo 50 caracteres")
    }

    @Test
    fun `PUT deve responder 400 com timezone vazio ou so de espacos`() {
        atualizar("""{"timezone":""}""").expectValidationFailed("timezone", "Timezone não pode ser vazio")
        atualizar("""{"timezone":"  "}""").expectValidationFailed("timezone", "Timezone não pode ser vazio")
    }

    @Test
    fun `PUT deve responder 400 com timezone nao suportado pelo dispositivo`() {
        atualizar("""{"timezone":"BRT"}""")
            .expectValidationFailed("timezone", "Timezone não suportado pelo dispositivo")
    }

    @Test
    fun `PUT deve aceitar America Sao Paulo`() {
        atualizar("""{"timezone":"America/Sao_Paulo"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"))
    }

    @Test
    fun `PUT sem timezone deve manter o timezone`() {
        atualizar("""{"nome":"Cafeteira Nova"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"))
    }

    // --- POST ---

    @Test
    fun `POST deve responder 400 com duracao acima de 900`() {
        criar("""{"nome":"Nova","duracaoPreparoS":901}""")
            .expectValidationFailed("duracaoPreparoS", "Duração de preparo deve ter no máximo 900s")
    }

    @Test
    fun `POST deve responder 400 com limiar zero`() {
        criar("""{"nome":"Nova","limiarDescalcificacao":0}""")
            .expectValidationFailed("limiarDescalcificacao", "Limiar de descalcificação deve ser maior que zero")
    }

    @Test
    fun `POST deve responder 400 em problem details com timezone de 51 caracteres`() {
        // Antes da validação, a exceção do banco caía no tratamento genérico e virava 500.
        criar("""{"nome":"Nova","timezone":"${"a".repeat(51)}"}""")
            .expectValidationFailed("timezone", "Timezone deve ter no máximo 50 caracteres")
    }

    @Test
    fun `POST deve responder 400 com timezone vazio`() {
        criar("""{"nome":"Nova","timezone":""}""")
            .expectValidationFailed("timezone", "Timezone não pode ser vazio")
    }

    @Test
    fun `POST deve responder 400 com timezone nao suportado pelo dispositivo`() {
        criar("""{"nome":"Nova","timezone":"BRT"}""")
            .expectValidationFailed("timezone", "Timezone não suportado pelo dispositivo")
    }

    @Test
    fun `POST deve aceitar America Sao Paulo`() {
        criar("""{"nome":"Nova","timezone":"America/Sao_Paulo"}""")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"))
    }

    @Test
    fun `POST deve responder 400 com nome de 256 caracteres`() {
        criar("""{"nome":"${"a".repeat(256)}"}""")
            .expectValidationFailed("nome", "Nome do dispositivo deve ter no máximo 255 caracteres")
    }

    @Test
    fun `POST sem timezone deve gravar America Sao_Paulo`() {
        criar("""{"nome":"Nova"}""")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"))
            .andExpect(jsonPath("$.duracaoPreparoS").value(300))

        assertEquals(2, usuarioDispositivoRepository.findByUsuarioId(proprietario.id!!).size)
    }
}
