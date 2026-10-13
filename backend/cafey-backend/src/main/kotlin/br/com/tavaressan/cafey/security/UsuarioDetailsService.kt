package br.com.tavaressan.cafey.security

import br.com.tavaressan.cafey.user.UsuarioRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

/**
 * Carrega o usuário pelo e-mail para o `DaoAuthenticationProvider` do signin (RF-18).
 *
 * O e-mail é normalizado (`trim` e minúsculas), como no cadastro. Usuário inexistente lança
 * [UsernameNotFoundException]: o provider a converte em `BadCredentialsException`, e o signin
 * responde o mesmo 401 de uma senha errada (sem revelar se o e-mail existe).
 */
@Service
class UsuarioDetailsService(
    private val usuarioRepository: UsuarioRepository
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val usuario = usuarioRepository.findByEmail(username.trim().lowercase())
            ?: throw UsernameNotFoundException("Usuário não encontrado")
        return UsuarioDetails(usuario)
    }
}
