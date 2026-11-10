package br.com.tavaressan.cafey.device

import br.com.tavaressan.cafey.event.EventoPreparo
import br.com.tavaressan.cafey.event.EventoPreparoRepository
import br.com.tavaressan.cafey.security.JwtTokenService
import br.com.tavaressan.cafey.user.Usuario
import br.com.tavaressan.cafey.user.UsuarioRepository
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class DispositivoDiagnosticoControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val usuarioRepository: UsuarioRepository,
    private val dispositivoRepository: DispositivoRepository,
    private val usuarioDispositivoRepository: UsuarioDispositivoRepository,
    private val eventoPreparoRepository: EventoPreparoRepository,
    private val jwtTokenService: JwtTokenService
) {
    private lateinit var proprietario: Usuario
    private lateinit var convidado: Usuario
    private lateinit var estranho: Usuario
    private lateinit var dispositivo: Dispositivo

    @BeforeEach
    fun setUp() {
        proprietario = criarUsuario("owner-diag")
        convidado = criarUsuario("guest-diag")
        estranho = criarUsuario("stranger-diag")
        dispositivo = dispositivoRepository.save(
            Dispositivo(
                nome = "Cafeteira Diagnostico",
                estado = "PREPARANDO",
                online = true,
                rssi = -58,
                uptimeS = 7200,
                firmware = "2.0.1",
                versaoAgendamentos = 3,
                ultimoVisto = Instant.parse("2026-11-09T12:00:00Z")
            )
        )
        vincular(proprietario, dispositivo, PapelDispositivo.PROPRIETARIO)
        vincular(convidado, dispositivo, PapelDispositivo.CONVIDADO)
        eventoPreparoRepository.save(
            EventoPreparo(
                eventoId = "evt-erro-diag",
                dispositivo = dispositivo,
                tipo = "PREPARO",
                resultado = "ERRO",
                origem = "APP",
                duracaoS = 0,
                timestamp = Instant.parse("2026-11-09T11:59:00Z"),
                detalheErro = "falha no aquecimento"
            )
        )
    }

    @AfterEach
    fun tearDown() {
        eventoPreparoRepository.deleteAll(eventoPreparoRepository.findAll().filter { it.dispositivo.id == dispositivo.id })
        usuarioDispositivoRepository.findByDispositivoId(dispositivo.id!!).forEach(usuarioDispositivoRepository::delete)
        dispositivoRepository.delete(dispositivo)
        listOf(proprietario, convidado, estranho).forEach(usuarioRepository::delete)
    }

    @Test
    fun `proprietario acessa diagnostico com valores reais e erros recentes`() {
        mockMvc.perform(
            get("/dispositivos/${dispositivo.id}/diagnostico")
                .header(HttpHeaders.AUTHORIZATION, bearer(proprietario))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.dispositivoId").value(dispositivo.id.toString()))
            .andExpect(jsonPath("$.online").value(true))
            .andExpect(jsonPath("$.estado").value("PREPARANDO"))
            .andExpect(jsonPath("$.rssi").value(-58))
            .andExpect(jsonPath("$.firmware").value("2.0.1"))
            .andExpect(jsonPath("$.uptimeS").value(7200))
            .andExpect(jsonPath("$.versaoAgendamentos").value(3))
            .andExpect(jsonPath("$.estadoRele").value(nullValue()))
            .andExpect(jsonPath("$.errosRecentes[0].eventoId").value("evt-erro-diag"))
            .andExpect(jsonPath("$.errosRecentes[0].detalheErro").value("falha no aquecimento"))
    }

    @Test
    fun `convidado nao acessa diagnostico`() {
        mockMvc.perform(
            get("/dispositivos/${dispositivo.id}/diagnostico")
                .header(HttpHeaders.AUTHORIZATION, bearer(convidado))
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `estranho nao acessa diagnostico`() {
        mockMvc.perform(
            get("/dispositivos/${dispositivo.id}/diagnostico")
                .header(HttpHeaders.AUTHORIZATION, bearer(estranho))
        )
            .andExpect(status().isNotFound)
    }

    private fun bearer(usuario: Usuario) =
        "Bearer " + jwtTokenService.generateAccessToken(usuario.id!!, usuario.email)

    private fun criarUsuario(prefixo: String) = usuarioRepository.save(
        Usuario(nome = prefixo, email = "$prefixo-${UUID.randomUUID()}@cafey.com", senhaHash = "hash")
    )

    private fun vincular(usuario: Usuario, dispositivo: Dispositivo, papel: PapelDispositivo) {
        usuarioDispositivoRepository.save(UsuarioDispositivo(usuario = usuario, dispositivo = dispositivo, papel = papel))
    }
}
