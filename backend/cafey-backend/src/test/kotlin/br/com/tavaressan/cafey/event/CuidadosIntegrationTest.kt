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
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

/**
 * Issue #190 — contadores de enxágue e filtro derivados de `eventos_preparo` (sem coluna nova),
 * com repositórios reais (H2) e HTTP via MockMvc. Os testes unitários de EventoServiceTest mockam
 * o repositório e não provam que as consultas JPQL de [EventoPreparoRepository] funcionam; este
 * cobre a derivação ponta a ponta e os nomes de campo do contrato JSON consumido pelo app.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CuidadosIntegrationTest @Autowired constructor(
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
    private lateinit var dispositivo: Dispositivo

    @BeforeEach
    fun setUp() {
        proprietario = usuarioRepository.save(
            Usuario(nome = "Dona", email = "dona-${UUID.randomUUID()}@cafey.com", senhaHash = "hash")
        )
        convidado = usuarioRepository.save(
            Usuario(nome = "Convidado", email = "convidado-${UUID.randomUUID()}@cafey.com", senhaHash = "hash")
        )
        dispositivo = dispositivoRepository.save(Dispositivo(nome = "Cafeteira Cuidados", limiarDescalcificacao = 400))
        usuarioDispositivoRepository.save(
            UsuarioDispositivo(usuario = proprietario, dispositivo = dispositivo, papel = PapelDispositivo.PROPRIETARIO)
        )
        usuarioDispositivoRepository.save(
            UsuarioDispositivo(usuario = convidado, dispositivo = dispositivo, papel = PapelDispositivo.CONVIDADO)
        )
    }

    @AfterEach
    fun tearDown() {
        // O H2 (ddl-auto create-drop) não herda o ON DELETE CASCADE da migration V5.
        jdbcTemplate.update("DELETE FROM eventos_preparo WHERE dispositivo_id = ?", dispositivo.id)
        usuarioDispositivoRepository.findByDispositivoId(dispositivo.id!!).forEach { usuarioDispositivoRepository.delete(it) }
        dispositivoRepository.delete(dispositivo)
        usuarioRepository.delete(proprietario)
        usuarioRepository.delete(convidado)
    }

    private fun bearer(usuario: Usuario): String =
        "Bearer " + jwtTokenService.generateAccessToken(usuario.id!!, usuario.email)

    private fun registrarPreparos(quantidade: Int) {
        repeat(quantidade) {
            eventoPreparoRepository.save(
                EventoPreparo(
                    eventoId = "evt-${UUID.randomUUID()}",
                    dispositivo = dispositivo,
                    resultado = "CONCLUIDO",
                    origem = "APP",
                    duracaoS = 300,
                    timestamp = Instant.now()
                )
            )
        }
        // Garante instantes de ingestão distintos entre lotes: a derivação ordena por criado_em.
        Thread.sleep(20)
    }

    @Test
    fun `deve expor os tres cuidados com contador limiar e destaque`() {
        registrarPreparos(34)

        mockMvc.perform(get("/dispositivos/${dispositivo.id}/cuidados").header(HttpHeaders.AUTHORIZATION, bearer(convidado)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.enxague.contadorPreparos").value(34))
            .andExpect(jsonPath("$.enxague.limiarPreparos").value(40))
            .andExpect(jsonPath("$.enxague.precisaAtencao").value(false))
            .andExpect(jsonPath("$.enxague.percentualUso").value(85.0))
            .andExpect(jsonPath("$.filtro.contadorPreparos").value(34))
            .andExpect(jsonPath("$.filtro.limiarPreparos").value(300))
            .andExpect(jsonPath("$.descalcificacao.limiarDescalcificacao").value(400))
            .andExpect(jsonPath("$.descalcificacao.precisaDescalcificar").value(false))
            .andExpect(jsonPath("$.destaque").value("ENXAGUE"))
    }

    @Test
    fun `baixa do enxague zera so o enxague e depois conta apenas os novos preparos`() {
        registrarPreparos(10)

        mockMvc.perform(post("/dispositivos/${dispositivo.id}/enxague/baixa").header(HttpHeaders.AUTHORIZATION, bearer(proprietario)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.contadorPreparos").value(0))
            .andExpect(jsonPath("$.limiarPreparos").value(40))
        Thread.sleep(20)
        registrarPreparos(3)

        mockMvc.perform(get("/dispositivos/${dispositivo.id}/cuidados").header(HttpHeaders.AUTHORIZATION, bearer(proprietario)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.enxague.contadorPreparos").value(3))
            .andExpect(jsonPath("$.filtro.contadorPreparos").value(13))
    }

    @Test
    fun `baixa do filtro nao afeta o enxague`() {
        registrarPreparos(5)

        mockMvc.perform(post("/dispositivos/${dispositivo.id}/filtro/baixa").header(HttpHeaders.AUTHORIZATION, bearer(proprietario)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.limiarPreparos").value(300))
        Thread.sleep(20)

        mockMvc.perform(get("/dispositivos/${dispositivo.id}/cuidados").header(HttpHeaders.AUTHORIZATION, bearer(proprietario)))
            .andExpect(jsonPath("$.filtro.contadorPreparos").value(0))
            .andExpect(jsonPath("$.enxague.contadorPreparos").value(5))
    }

    @Test
    fun `convidado nao pode dar baixa`() {
        mockMvc.perform(post("/dispositivos/${dispositivo.id}/enxague/baixa").header(HttpHeaders.AUTHORIZATION, bearer(convidado)))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(post("/dispositivos/${dispositivo.id}/filtro/baixa").header(HttpHeaders.AUTHORIZATION, bearer(convidado)))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `marcadores de baixa nao aparecem no historico nem nas estatisticas`() {
        registrarPreparos(2)
        mockMvc.perform(post("/dispositivos/${dispositivo.id}/enxague/baixa").header(HttpHeaders.AUTHORIZATION, bearer(proprietario)))
            .andExpect(status().isOk)

        mockMvc.perform(get("/dispositivos/${dispositivo.id}/eventos").header(HttpHeaders.AUTHORIZATION, bearer(proprietario)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(2))
        mockMvc.perform(get("/dispositivos/${dispositivo.id}/estatisticas").header(HttpHeaders.AUTHORIZATION, bearer(proprietario)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalPreparosConcluidos").value(2))
    }
}
