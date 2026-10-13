package br.com.tavaressan.cafey.security

import br.com.tavaressan.cafey.user.Usuario
import br.com.tavaressan.cafey.user.UsuarioRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.security.core.userdetails.UsernameNotFoundException
import java.util.UUID

class UsuarioDetailsServiceTest {

    private val usuarioRepository = mock(UsuarioRepository::class.java)
    private val service = UsuarioDetailsService(usuarioRepository)

    private fun usuario() = Usuario(
        id = UUID.randomUUID(),
        nome = "Test User",
        email = "test@cafey.com",
        senhaHash = "hash-bcrypt"
    )

    @Test
    fun `carrega o usuario pelo email normalizado`() {
        val usuario = usuario()
        `when`(usuarioRepository.findByEmail("test@cafey.com")).thenReturn(usuario)

        val details = service.loadUserByUsername("  Test@Cafey.COM ") as UsuarioDetails

        assertSame(usuario, details.usuario)
        assertEquals("test@cafey.com", details.username)
        assertEquals("hash-bcrypt", details.password)
    }

    @Test
    fun `usuario inexistente lanca UsernameNotFoundException`() {
        `when`(usuarioRepository.findByEmail("ninguem@cafey.com")).thenReturn(null)

        assertThrows<UsernameNotFoundException> { service.loadUserByUsername("ninguem@cafey.com") }
    }

    @Test
    fun `limpar as credenciais do principal nao altera o senhaHash da entidade`() {
        val usuario = usuario()
        `when`(usuarioRepository.findByEmail("test@cafey.com")).thenReturn(usuario)
        val details = service.loadUserByUsername("test@cafey.com") as UsuarioDetails

        details.eraseCredentials()

        assertEquals("hash-bcrypt", usuario.senhaHash)
    }
}
