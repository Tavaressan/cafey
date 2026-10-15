package br.com.tavaressan.cafey.device

import br.com.tavaressan.cafey.security.JwtTokenService
import br.com.tavaressan.cafey.user.Usuario
import br.com.tavaressan.cafey.user.UsuarioRepository
import org.hamcrest.Matchers.hasSize
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
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class DispositivoBuscaControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val usuarioRepository: UsuarioRepository,
    private val dispositivoRepository: DispositivoRepository,
    private val usuarioDispositivoRepository: UsuarioDispositivoRepository,
    private val jwtTokenService: JwtTokenService
) {
    private lateinit var proprietario: Usuario
    private lateinit var convidado: Usuario
    private lateinit var outroUsuario: Usuario
    private lateinit var cozinha: Dispositivo
    private lateinit var escritorio: Dispositivo
    private lateinit var dispositivoConvidado: Dispositivo
    private lateinit var dispositivoAlheio: Dispositivo

    @BeforeEach
    fun setUp() {
        proprietario = criarUsuario("owner")
        convidado = criarUsuario("guest")
        outroUsuario = criarUsuario("other")
        cozinha = criarDispositivo("Cafeteira da Cozinha")
        escritorio = criarDispositivo("Cafeteira do Escritório")
        dispositivoConvidado = criarDispositivo("Moedor do Convidado")
        dispositivoAlheio = criarDispositivo("Cafeteira da Cozinha Privada")
        vincular(proprietario, cozinha, PapelDispositivo.PROPRIETARIO)
        vincular(proprietario, escritorio, PapelDispositivo.PROPRIETARIO)
        vincular(convidado, cozinha, PapelDispositivo.CONVIDADO)
        vincular(convidado, dispositivoConvidado, PapelDispositivo.PROPRIETARIO)
        vincular(outroUsuario, dispositivoAlheio, PapelDispositivo.PROPRIETARIO)
    }

    @AfterEach
    fun tearDown() {
        listOf(cozinha, escritorio, dispositivoConvidado, dispositivoAlheio).forEach { dispositivo ->
            usuarioDispositivoRepository.findByDispositivoId(dispositivo.id!!).forEach(usuarioDispositivoRepository::delete)
            dispositivoRepository.delete(dispositivo)
        }
        listOf(proprietario, convidado, outroUsuario).forEach(usuarioRepository::delete)
    }

    @Test
    fun `filtro de nome trata ausencia branco e espacos e mantem papeis e escopo`() {
        requisicao(proprietario, "coz")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
            .andExpect(jsonPath("$[0].nome").value("Cafeteira da Cozinha"))

        requisicao(proprietario, "sem correspondencia")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))

        requisicao(proprietario)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(2)))
        requisicao(proprietario, "")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(2)))
        requisicao(proprietario, "  ")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(2)))
        requisicao(proprietario, " coz ")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
            .andExpect(jsonPath("$[0].nome").value("Cafeteira da Cozinha"))

        // A busca ordena por nome, então o papel de cada item pode ser conferido pela posição.
        requisicao(convidado, "co")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(2)))
            .andExpect(jsonPath("$[0].nome").value("Cafeteira da Cozinha"))
            .andExpect(jsonPath("$[0].papel").value("CONVIDADO"))
            .andExpect(jsonPath("$[1].nome").value("Moedor do Convidado"))
            .andExpect(jsonPath("$[1].papel").value("PROPRIETARIO"))
        requisicao(outroUsuario, "coz")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
            .andExpect(jsonPath("$[0].nome").value("Cafeteira da Cozinha Privada"))
    }

    private fun requisicao(usuario: Usuario, nome: String? = null) = mockMvc.perform(
        get("/dispositivos")
            .apply { if (nome != null) param("nome", nome) }
            .header(HttpHeaders.AUTHORIZATION, bearer(usuario))
    )

    private fun bearer(usuario: Usuario) =
        "Bearer " + jwtTokenService.generateAccessToken(usuario.id!!, usuario.email)

    private fun criarUsuario(prefixo: String) = usuarioRepository.save(
        Usuario(nome = prefixo, email = "$prefixo-${UUID.randomUUID()}@cafey.com", senhaHash = "hash")
    )

    private fun criarDispositivo(nome: String) = dispositivoRepository.save(Dispositivo(nome = nome))

    private fun vincular(usuario: Usuario, dispositivo: Dispositivo, papel: PapelDispositivo) {
        usuarioDispositivoRepository.save(UsuarioDispositivo(usuario = usuario, dispositivo = dispositivo, papel = papel))
    }
}
