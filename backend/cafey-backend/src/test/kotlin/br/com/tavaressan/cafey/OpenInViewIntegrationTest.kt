package br.com.tavaressan.cafey

import br.com.tavaressan.cafey.device.Dispositivo
import br.com.tavaressan.cafey.device.DispositivoRepository
import br.com.tavaressan.cafey.device.PapelDispositivo
import br.com.tavaressan.cafey.device.UsuarioDispositivo
import br.com.tavaressan.cafey.device.UsuarioDispositivoRepository
import br.com.tavaressan.cafey.schedule.Agendamento
import br.com.tavaressan.cafey.schedule.AgendamentoRepository
import br.com.tavaressan.cafey.security.JwtTokenService
import br.com.tavaressan.cafey.user.Usuario
import br.com.tavaressan.cafey.user.UsuarioRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalTime
import java.util.UUID

/**
 * Testes de integração ponta a ponta (HTTP real via MockMvc + banco H2 real, sem mocks de
 * repositório) para o critério de aceite da issue #108: com `spring.jpa.open-in-view: false`,
 * as listagens não podem depender de lazy loading fora da transação do serviço.
 *
 * Diferente dos testes unitários existentes (ex.: DispositivoServiceTest), que usam Mockito e
 * nunca criam um Hibernate Session de verdade, aqui os dados são persistidos e lidos de volta
 * via repositórios reais, e o corpo da resposta é serializado pelo Jackson somente depois que o
 * método `@Transactional` do serviço já retornou — exatamente o ponto onde uma associação LAZY
 * não carregada explicitamente estouraria LazyInitializationException com OSIV desligado.
 *
 * Não há `@Transactional` na classe de teste propositalmente: se houvesse, o teste herdaria uma
 * transação/sessão aberta durante toda a chamada MockMvc, mascarando o problema que estas
 * asserções existem para pegar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class OpenInViewIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val usuarioRepository: UsuarioRepository,
    private val dispositivoRepository: DispositivoRepository,
    private val usuarioDispositivoRepository: UsuarioDispositivoRepository,
    private val agendamentoRepository: AgendamentoRepository,
    private val jwtTokenService: JwtTokenService
) {

    private lateinit var proprietario: Usuario
    private lateinit var convidado: Usuario
    private lateinit var dispositivo: Dispositivo

    @BeforeEach
    fun setUp() {
        proprietario = usuarioRepository.save(
            Usuario(nome = "Dona Cafeteira", email = "dona-${UUID.randomUUID()}@cafey.com", senhaHash = "hash")
        )
        convidado = usuarioRepository.save(
            Usuario(nome = "Convidado Cafeteira", email = "convidado-${UUID.randomUUID()}@cafey.com", senhaHash = "hash")
        )
        dispositivo = dispositivoRepository.save(
            Dispositivo(nome = "Cafeteira Integração", duracaoPreparoS = 300)
        )
        usuarioDispositivoRepository.save(
            UsuarioDispositivo(usuario = proprietario, dispositivo = dispositivo, papel = PapelDispositivo.PROPRIETARIO)
        )
        usuarioDispositivoRepository.save(
            UsuarioDispositivo(usuario = convidado, dispositivo = dispositivo, papel = PapelDispositivo.CONVIDADO)
        )
    }

    @AfterEach
    fun tearDown() {
        agendamentoRepository.findByDispositivoId(dispositivo.id!!).forEach { agendamentoRepository.delete(it) }
        usuarioDispositivoRepository.findByDispositivoId(dispositivo.id!!).forEach { usuarioDispositivoRepository.delete(it) }
        dispositivoRepository.delete(dispositivo)
        usuarioRepository.delete(proprietario)
        usuarioRepository.delete(convidado)
    }

    private fun bearerTokenPara(usuario: Usuario): String =
        "Bearer " + jwtTokenService.generateAccessToken(usuario.id!!, usuario.email)

    @Test
    fun `deve listar dispositivos do usuario sem LazyInitializationException`() {
        mockMvc.perform(
            get("/dispositivos")
                .header(HttpHeaders.AUTHORIZATION, bearerTokenPara(proprietario))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(dispositivo.id.toString()))
            .andExpect(jsonPath("$[0].nome").value("Cafeteira Integração"))
            .andExpect(jsonPath("$[0].papel").value("PROPRIETARIO"))
    }

    @Test
    fun `deve listar agendamentos do dispositivo sem LazyInitializationException`() {
        agendamentoRepository.save(
            Agendamento(dispositivo = dispositivo, hora = LocalTime.of(7, 30), diasSemana = 127)
        )

        mockMvc.perform(
            get("/dispositivos/${dispositivo.id}/agendamentos")
                .header(HttpHeaders.AUTHORIZATION, bearerTokenPara(proprietario))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].dispositivoId").value(dispositivo.id.toString()))
            .andExpect(jsonPath("$[0].hora").value("07:30"))
    }

    @Test
    fun `deve listar compartilhamentos do dispositivo sem LazyInitializationException`() {
        mockMvc.perform(
            get("/dispositivos/${dispositivo.id}/compartilhamentos")
                .header(HttpHeaders.AUTHORIZATION, bearerTokenPara(proprietario))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[?(@.papel == 'CONVIDADO')].email").value(convidado.email))
    }
}
