package br.com.tavaressan.cafey.security

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import java.util.Base64
import java.util.UUID

/**
 * Testes unitários do [JwtAuthenticationFilter] (RF-23): o que o filtro coloca (ou não) no
 * `SecurityContext` e se a requisição segue a cadeia. A resposta HTTP (401 em rota protegida) é
 * decidida pela regra de autorização e coberta em [SecurityChainIntegrationTest].
 */
class JwtAuthenticationFilterTest {

    private val emissor = TestJwtIssuer()
    private val jwtTokenService = emissor.service()
    private val filter = JwtAuthenticationFilter(jwtTokenService)

    @AfterEach
    fun limparContexto() {
        SecurityContextHolder.clearContext()
    }

    /** Resultado de uma passagem pelo filtro: a autenticação vista pela cadeia e quantas vezes ela rodou. */
    private class Passagem(val autenticacao: Authentication?, val vezesQueACadeiaRodou: Int)

    private fun executar(authorization: String?): Passagem {
        val request = MockHttpServletRequest("GET", "/dispositivos")
        if (authorization != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, authorization)
        }
        var autenticacao: Authentication? = null
        var vezes = 0
        val chain = FilterChain { _, _ ->
            vezes++
            autenticacao = SecurityContextHolder.getContext().authentication
        }
        filter.doFilter(request, MockHttpServletResponse(), chain)
        return Passagem(autenticacao, vezes)
    }

    @Test
    fun `sem cabecalho Authorization segue a cadeia sem autenticar`() {
        val passagem = executar(authorization = null)

        assertEquals(1, passagem.vezesQueACadeiaRodou)
        assertNull(passagem.autenticacao)
    }

    @Test
    fun `token valido autentica com o Jwt como principal`() {
        val usuarioId = UUID.randomUUID()
        val token = jwtTokenService.generateAccessToken(usuarioId, "test@cafey.com")

        val passagem = executar("Bearer $token")

        assertEquals(1, passagem.vezesQueACadeiaRodou)
        val autenticacao = passagem.autenticacao
        assertNotNull(autenticacao)
        assertTrue(autenticacao!!.isAuthenticated)
        val principal = autenticacao.principal as Jwt
        assertEquals(usuarioId.toString(), principal.subject)
    }

    @Test
    fun `esquema Bearer e aceito sem diferenciar maiusculas`() {
        val token = jwtTokenService.generateAccessToken(UUID.randomUUID(), "test@cafey.com")

        val passagem = executar("bearer $token")

        assertTrue(passagem.autenticacao!!.isAuthenticated)
    }

    @Test
    fun `esquema diferente de Bearer segue sem autenticar`() {
        val credenciais = Base64.getEncoder().encodeToString("usuario:senha".toByteArray())

        val passagem = executar("Basic $credenciais")

        assertEquals(1, passagem.vezesQueACadeiaRodou)
        assertNull(passagem.autenticacao)
    }

    @Test
    fun `Bearer com token vazio segue sem autenticar`() {
        listOf("Bearer ", "Bearer", "Bearer    ").forEach { cabecalho ->
            val passagem = executar(cabecalho)

            assertEquals(1, passagem.vezesQueACadeiaRodou, cabecalho)
            assertNull(passagem.autenticacao, cabecalho)
        }
    }

    @Test
    fun `token malformado segue a cadeia sem autenticar`() {
        val passagem = executar("Bearer abc")

        assertEquals(1, passagem.vezesQueACadeiaRodou)
        assertNull(passagem.autenticacao)
    }

    @Test
    fun `token expirado segue a cadeia sem autenticar`() {
        val passagem = executar("Bearer ${emissor.emitirExpirado()}")

        assertEquals(1, passagem.vezesQueACadeiaRodou)
        assertNull(passagem.autenticacao)
    }

    @Test
    fun `token assinado por outra chave segue a cadeia sem autenticar`() {
        val tokenDeOutraChave = TestJwtIssuer().emitir(UUID.randomUUID().toString())

        val passagem = executar("Bearer $tokenDeOutraChave")

        assertEquals(1, passagem.vezesQueACadeiaRodou)
        assertNull(passagem.autenticacao)
    }

    @Test
    fun `token com sub fora do formato UUID nao autentica`() {
        val passagem = executar("Bearer ${emissor.emitir(subject = "nao-e-um-uuid")}")

        assertEquals(1, passagem.vezesQueACadeiaRodou)
        assertNull(passagem.autenticacao)
    }

    @Test
    fun `token sem sub nao autentica`() {
        val passagem = executar("Bearer ${emissor.emitir(subject = null)}")

        assertEquals(1, passagem.vezesQueACadeiaRodou)
        assertNull(passagem.autenticacao)
    }

    @Test
    fun `token invalido limpa um contexto de seguranca que ja existia`() {
        val contexto = SecurityContextHolder.createEmptyContext()
        contexto.authentication = UsernamePasswordAuthenticationToken.authenticated("alguem", "x", emptyList())
        SecurityContextHolder.setContext(contexto)

        val passagem = executar("Bearer abc")

        assertEquals(1, passagem.vezesQueACadeiaRodou)
        assertNull(passagem.autenticacao)
        assertNull(SecurityContextHolder.getContext().authentication)
    }
}
