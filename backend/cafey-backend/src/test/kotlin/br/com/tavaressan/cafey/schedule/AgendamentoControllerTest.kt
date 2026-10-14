package br.com.tavaressan.cafey.schedule

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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalTime
import java.util.UUID

/**
 * Testes HTTP (MockMvc, banco H2 real) do `GET /dispositivos/{dispositivoId}/agendamentos/{agendamentoId}`.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AgendamentoControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val usuarioRepository: UsuarioRepository,
    private val dispositivoRepository: DispositivoRepository,
    private val usuarioDispositivoRepository: UsuarioDispositivoRepository,
    private val agendamentoRepository: AgendamentoRepository,
    private val jwtTokenService: JwtTokenService
) {

    private lateinit var proprietario: Usuario
    private lateinit var estranho: Usuario
    private lateinit var dispositivo: Dispositivo
    private lateinit var outroDispositivo: Dispositivo
    private lateinit var agendamento: Agendamento
    private lateinit var agendamentoDoOutro: Agendamento

    @BeforeEach
    fun setUp() {
        proprietario = usuarioRepository.save(
            Usuario(nome = "Dona Cafeteira", email = "dona-${UUID.randomUUID()}@cafey.com", senhaHash = "hash")
        )
        estranho = usuarioRepository.save(
            Usuario(nome = "Sem Vinculo", email = "estranho-${UUID.randomUUID()}@cafey.com", senhaHash = "hash")
        )
        dispositivo = dispositivoRepository.save(Dispositivo(nome = "Cafeteira Cozinha"))
        outroDispositivo = dispositivoRepository.save(Dispositivo(nome = "Cafeteira Escritorio"))
        // A dona tem vínculo com os dois dispositivos: o 404 do agendamento alheio vale mesmo assim.
        listOf(dispositivo, outroDispositivo).forEach {
            usuarioDispositivoRepository.save(
                UsuarioDispositivo(usuario = proprietario, dispositivo = it, papel = PapelDispositivo.PROPRIETARIO)
            )
        }
        agendamento = agendamentoRepository.save(
            Agendamento(
                dispositivo = dispositivo, hora = LocalTime.of(7, 30), diasSemana = 62, ativo = true, duracaoPreparoS = 480,
                descricao = "Cafe da manha"
            )
        )
        agendamentoDoOutro = agendamentoRepository.save(
            Agendamento(dispositivo = outroDispositivo, hora = LocalTime.of(9, 0), diasSemana = 1)
        )
    }

    @AfterEach
    fun tearDown() {
        agendamentoRepository.delete(agendamento)
        agendamentoRepository.delete(agendamentoDoOutro)
        listOf(dispositivo, outroDispositivo).forEach { disp ->
            usuarioDispositivoRepository.findByDispositivoId(disp.id!!).forEach { usuarioDispositivoRepository.delete(it) }
            dispositivoRepository.delete(disp)
        }
        usuarioRepository.delete(proprietario)
        usuarioRepository.delete(estranho)
    }

    private fun bearerTokenPara(usuario: Usuario): String =
        "Bearer " + jwtTokenService.generateAccessToken(usuario.id!!, usuario.email)

    @Test
    fun `deve devolver o agendamento pelo id com 200`() {
        mockMvc.perform(
            get("/dispositivos/${dispositivo.id}/agendamentos/${agendamento.id}")
                .header(HttpHeaders.AUTHORIZATION, bearerTokenPara(proprietario))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(agendamento.id.toString()))
            .andExpect(jsonPath("$.dispositivoId").value(dispositivo.id.toString()))
            .andExpect(jsonPath("$.hora").value("07:30"))
            .andExpect(jsonPath("$.diasSemana").value(62))
            .andExpect(jsonPath("$.ativo").value(true))
            .andExpect(jsonPath("$.duracaoPreparoS").value(480))
            .andExpect(jsonPath("$.descricao").value("Cafe da manha"))
    }

    @Test
    fun `deve responder 404 em problem details quando o agendamento nao existe`() {
        mockMvc.perform(
            get("/dispositivos/${dispositivo.id}/agendamentos/${UUID.randomUUID()}")
                .header(HttpHeaders.AUTHORIZATION, bearerTokenPara(proprietario))
        )
            .andExpect(status().isNotFound)
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("Agendamento não encontrado"))
    }

    @Test
    fun `deve responder 404 quando o agendamento pertence a outro dispositivo do mesmo usuario`() {
        mockMvc.perform(
            get("/dispositivos/${dispositivo.id}/agendamentos/${agendamentoDoOutro.id}")
                .header(HttpHeaders.AUTHORIZATION, bearerTokenPara(proprietario))
        )
            .andExpect(status().isNotFound)
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("Agendamento não pertence a este dispositivo"))
    }

    @Test
    fun `deve responder 404 para usuario sem vinculo com o dispositivo`() {
        mockMvc.perform(
            get("/dispositivos/${dispositivo.id}/agendamentos/${agendamento.id}")
                .header(HttpHeaders.AUTHORIZATION, bearerTokenPara(estranho))
        )
            .andExpect(status().isNotFound)
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("Dispositivo não encontrado ou você não tem acesso"))
    }

    @Test
    fun `deve responder 401 sem token`() {
        mockMvc.perform(get("/dispositivos/${dispositivo.id}/agendamentos/${agendamento.id}"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `lista agendamentos filtrando descricao e trata parametro em branco como lista completa`() {
        mockMvc.perform(
            get("/dispositivos/${dispositivo.id}/agendamentos")
                .param("descricao", "  manha  ")
                .header(HttpHeaders.AUTHORIZATION, bearerTokenPara(proprietario))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].descricao").value("Cafe da manha"))

        mockMvc.perform(
            get("/dispositivos/${dispositivo.id}/agendamentos")
                .param("descricao", "   ")
                .header(HttpHeaders.AUTHORIZATION, bearerTokenPara(proprietario))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(agendamento.id.toString()))
    }

    @Test
    fun `busca com descricao em branco ainda retorna 404 para usuario sem vinculo`() {
        mockMvc.perform(
            get("/dispositivos/${dispositivo.id}/agendamentos")
                .param("descricao", "")
                .header(HttpHeaders.AUTHORIZATION, bearerTokenPara(estranho))
        )
            .andExpect(status().isNotFound)
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
    }
}
