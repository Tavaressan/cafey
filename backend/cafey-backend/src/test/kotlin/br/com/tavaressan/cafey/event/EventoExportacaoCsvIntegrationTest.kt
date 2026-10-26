package br.com.tavaressan.cafey.event

import br.com.tavaressan.cafey.device.Dispositivo
import br.com.tavaressan.cafey.device.DispositivoRepository
import br.com.tavaressan.cafey.device.PapelDispositivo
import br.com.tavaressan.cafey.device.UsuarioDispositivo
import br.com.tavaressan.cafey.device.UsuarioDispositivoRepository
import br.com.tavaressan.cafey.security.JwtTokenService
import br.com.tavaressan.cafey.user.Usuario
import br.com.tavaressan.cafey.user.UsuarioRepository
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.Instant
import java.util.UUID

/**
 * Issue #285 — Endpoint de exportação de histórico de preparos e eventos em formato CSV para análise acadêmica.
 * Valida autorização JWT, Content-Type text/csv (RFC 4180), cabeçalhos padronizados e cálculo de linhas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class EventoExportacaoCsvIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val usuarioRepository: UsuarioRepository,
    private val dispositivoRepository: DispositivoRepository,
    private val usuarioDispositivoRepository: UsuarioDispositivoRepository,
    private val eventoPreparoRepository: EventoPreparoRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val jwtTokenService: JwtTokenService
) {

    private lateinit var proprietario: Usuario
    private lateinit var convidado: Usuario
    private lateinit var outroUsuario: Usuario
    private lateinit var dispositivo: Dispositivo
    private lateinit var outroDispositivo: Dispositivo

    @BeforeEach
    fun setUp() {
        proprietario = usuarioRepository.save(
            Usuario(nome = "Proprietario", email = "proprietario-${UUID.randomUUID()}@cafey.com", senhaHash = "hash")
        )
        convidado = usuarioRepository.save(
            Usuario(nome = "Convidado", email = "convidado-${UUID.randomUUID()}@cafey.com", senhaHash = "hash")
        )
        outroUsuario = usuarioRepository.save(
            Usuario(nome = "Outro", email = "outro-${UUID.randomUUID()}@cafey.com", senhaHash = "hash")
        )

        dispositivo = dispositivoRepository.save(
            Dispositivo(nome = "Cafeteira Teste", duracaoPreparoS = 300)
        )
        outroDispositivo = dispositivoRepository.save(
            Dispositivo(nome = "Cafeteira Outra", duracaoPreparoS = 240)
        )

        usuarioDispositivoRepository.save(
            UsuarioDispositivo(usuario = proprietario, dispositivo = dispositivo, papel = PapelDispositivo.PROPRIETARIO)
        )
        usuarioDispositivoRepository.save(
            UsuarioDispositivo(usuario = convidado, dispositivo = dispositivo, papel = PapelDispositivo.CONVIDADO)
        )
        usuarioDispositivoRepository.save(
            UsuarioDispositivo(usuario = outroUsuario, dispositivo = outroDispositivo, papel = PapelDispositivo.PROPRIETARIO)
        )
    }

    @AfterEach
    fun tearDown() {
        jdbcTemplate.update("DELETE FROM eventos_preparo WHERE dispositivo_id IN (?, ?)", dispositivo.id, outroDispositivo.id)
        usuarioDispositivoRepository.findByDispositivoId(dispositivo.id!!).forEach { usuarioDispositivoRepository.delete(it) }
        usuarioDispositivoRepository.findByDispositivoId(outroDispositivo.id!!).forEach { usuarioDispositivoRepository.delete(it) }
        dispositivoRepository.delete(dispositivo)
        dispositivoRepository.delete(outroDispositivo)
        usuarioRepository.delete(proprietario)
        usuarioRepository.delete(convidado)
        usuarioRepository.delete(outroUsuario)
    }

    private fun bearer(usuario: Usuario): String =
        "Bearer " + jwtTokenService.generateAccessToken(usuario.id!!, usuario.email)

    @Test
    fun `deve retornar 401 Unauthorized quando sem token JWT`() {
        mockMvc.perform(get("/dispositivos/${dispositivo.id}/eventos/exportar"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `deve retornar 404 Not Found quando usuario nao possui acesso ao dispositivo`() {
        mockMvc.perform(
            get("/dispositivos/${dispositivo.id}/eventos/exportar")
                .header(HttpHeaders.AUTHORIZATION, bearer(outroUsuario))
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `deve exportar CSV vazio com cabecalho quando nao ha eventos`() {
        val result = mockMvc.perform(
            get("/dispositivos/${dispositivo.id}/eventos/exportar")
                .header(HttpHeaders.AUTHORIZATION, bearer(proprietario))
        )
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith("text/csv")))
            .andReturn()

        val csv = result.response.contentAsString
        assertEquals("evento_id,inicio,fim,duracao_s,origem,resultado\r\n", csv)
    }

    @Test
    fun `deve exportar CSV com cabecalho padronizado e linhas geradas corretamente para o proprietario`() {
        val t1 = Instant.parse("2026-10-23T08:00:00Z")
        val t2 = Instant.parse("2026-10-23T09:30:00Z")

        eventoPreparoRepository.save(
            EventoPreparo(
                eventoId = "boot-01:1",
                dispositivo = dispositivo,
                tipo = "PREPARO",
                resultado = "CONCLUIDO",
                origem = "APP",
                duracaoS = 300,
                timestamp = t1
            )
        )
        eventoPreparoRepository.save(
            EventoPreparo(
                eventoId = "boot-01:2",
                dispositivo = dispositivo,
                tipo = "PREPARO",
                resultado = "CANCELADO",
                origem = "BOTAO",
                duracaoS = 60,
                timestamp = t2
            )
        )
        // Marcador de cuidado (tipo BAIXA_ENXAGUE) não deve ser exportado como preparo acadêmico
        eventoPreparoRepository.save(
            EventoPreparo(
                eventoId = "BAIXA_ENXAGUE-uuid",
                dispositivo = dispositivo,
                tipo = "BAIXA_ENXAGUE",
                resultado = "BAIXA",
                origem = "APP",
                duracaoS = 0,
                timestamp = Instant.parse("2026-10-23T10:00:00Z")
            )
        )
        // Evento de outro dispositivo não deve constar no CSV
        eventoPreparoRepository.save(
            EventoPreparo(
                eventoId = "outro-disp:1",
                dispositivo = outroDispositivo,
                tipo = "PREPARO",
                resultado = "CONCLUIDO",
                origem = "AGENDAMENTO",
                duracaoS = 240,
                timestamp = Instant.parse("2026-10-23T08:15:00Z")
            )
        )

        val result = mockMvc.perform(
            get("/dispositivos/${dispositivo.id}/eventos/exportar")
                .header(HttpHeaders.AUTHORIZATION, bearer(proprietario))
        )
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith("text/csv")))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("eventos-${dispositivo.id}.csv")))
            .andReturn()

        val csv = result.response.contentAsString
        val lines = csv.split("\r\n").filter { it.isNotEmpty() }

        assertEquals(3, lines.size, "Deve ter o cabeçalho e exatamente 2 registros de preparo")
        assertEquals("evento_id,inicio,fim,duracao_s,origem,resultado", lines[0])
        assertEquals("boot-01:1,2026-10-23T08:00:00Z,2026-10-23T08:05:00Z,300,APP,CONCLUIDO", lines[1])
        assertEquals("boot-01:2,2026-10-23T09:30:00Z,2026-10-23T09:31:00Z,60,BOTAO,CANCELADO", lines[2])
    }

    @Test
    fun `deve permitir exportacao por usuario convidado com vinculo`() {
        val t1 = Instant.parse("2026-10-23T14:00:00Z")
        eventoPreparoRepository.save(
            EventoPreparo(
                eventoId = "boot-02:1",
                dispositivo = dispositivo,
                tipo = "PREPARO",
                resultado = "CONCLUIDO",
                origem = "AGENDAMENTO",
                duracaoS = 180,
                timestamp = t1
            )
        )

        val result = mockMvc.perform(
            get("/dispositivos/${dispositivo.id}/eventos/exportar")
                .header(HttpHeaders.AUTHORIZATION, bearer(convidado))
        )
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith("text/csv")))
            .andReturn()

        val csv = result.response.contentAsString
        assertTrue(csv.contains("boot-02:1,2026-10-23T14:00:00Z,2026-10-23T14:03:00Z,180,AGENDAMENTO,CONCLUIDO"))
    }
}
