package br.com.tavaressan.cafey.auth

import br.com.tavaressan.cafey.user.Usuario
import br.com.tavaressan.cafey.user.UsuarioRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fluxo de autenticação ponta a ponta (cadeia de segurança real, `AuthenticationManager` real,
 * banco H2) para `/auth/signup` e `/auth/signin` (RF-17 a RF-19).
 *
 * Sem `@Transactional` na classe de propósito: o `senhaHash` é relido do banco depois do signin, e
 * uma transação de teste mascararia uma alteração da entidade feita pelo Spring Security (o
 * `ProviderManager` apaga as credenciais do principal depois de autenticar).
 *
 * Cada requisição usa um IP próprio, porque o rate limit de `/auth/signin` e `/auth/login` é por
 * IP, e o contexto de teste (e o mapa de baldes) é compartilhado com outras classes. Os e-mails
 * são únicos por execução, então os usuários criados ficam no H2 em memória sem atrapalhar outros
 * testes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AuthSigninIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val usuarioRepository: UsuarioRepository,
    private val passwordEncoder: PasswordEncoder
) {

    private companion object {
        const val SENHA = "senha-segura-123"
        val proximoIp = AtomicInteger(1)
        val tokenNoCorpo = Regex("\"accessToken\"\\s*:\\s*\"([^\"]+)\"")
    }

    private lateinit var usuario: Usuario

    @BeforeEach
    fun setUp() {
        usuario = usuarioRepository.save(
            Usuario(
                nome = "Dona Cafeteira",
                email = "signin-${UUID.randomUUID()}@cafey.com",
                senhaHash = passwordEncoder.encode(SENHA)!!
            )
        )
    }

    private fun ipNovo() = "10.30.0.${proximoIp.getAndIncrement()}"

    private fun chamar(rota: String, corpo: String) = mockMvc.perform(
        post(rota)
            .with { request -> request.remoteAddr = ipNovo(); request }
            .contentType(MediaType.APPLICATION_JSON)
            .content(corpo)
    )

    private fun corpoSignin(email: String, senha: String) = """{"email":"$email","senha":"$senha"}"""

    @Test
    fun `signup e signin devolvem um access token aceito numa rota protegida`() {
        val email = "novo-${UUID.randomUUID()}@cafey.com"

        chamar("/auth/signup", """{"nome":"Novo","email":"$email","senha":"$SENHA"}""")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)

        val corpo = chamar("/auth/signin", corpoSignin(email, SENHA))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
            .andReturn().response.contentAsString
        val accessToken = tokenNoCorpo.find(corpo)!!.groupValues[1]

        mockMvc.perform(get("/dispositivos").header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"))
            .andExpect(status().isOk)
    }

    @Test
    fun `signin nao altera o senhaHash persistido do usuario`() {
        val hashAntes = usuarioRepository.findByEmail(usuario.email)!!.senhaHash

        chamar("/auth/signin", corpoSignin(usuario.email, SENHA)).andExpect(status().isOk)

        assertEquals(hashAntes, usuarioRepository.findByEmail(usuario.email)!!.senhaHash)
    }

    @Test
    fun `signin normaliza o email digitado`() {
        chamar("/auth/signin", corpoSignin(usuario.email.uppercase(), SENHA))
            .andExpect(status().isOk)
    }

    @Test
    fun `senha errada devolve 401 com mensagem generica`() {
        chamar("/auth/signin", corpoSignin(usuario.email, "senha-errada"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.detail").value("Email ou senha inválidos"))
    }

    @Test
    fun `email inexistente devolve o mesmo 401`() {
        chamar("/auth/signin", corpoSignin("ninguem-${UUID.randomUUID()}@cafey.com", SENHA))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.detail").value("Email ou senha inválidos"))
    }

    @Test
    fun `os aliases login e registrar continuam funcionando`() {
        val email = "alias-${UUID.randomUUID()}@cafey.com"

        chamar("/auth/registrar", """{"nome":"Alias","email":"$email","senha":"$SENHA"}""")
            .andExpect(status().isCreated)
        chamar("/auth/login", corpoSignin(email, SENHA))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
    }
}
