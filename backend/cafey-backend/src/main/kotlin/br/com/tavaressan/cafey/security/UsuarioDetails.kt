package br.com.tavaressan.cafey.security

import br.com.tavaressan.cafey.user.Usuario
import org.springframework.security.core.userdetails.User

/**
 * Principal do Spring Security para o signin: carrega a entidade [Usuario] junto das credenciais.
 *
 * Não sobrescreve `eraseCredentials` de propósito. Depois de autenticar, o `ProviderManager` apaga
 * as credenciais do principal; isso só afeta a cópia da senha guardada por [User], nunca o
 * `senhaHash` da entidade, que (dentro do `@Transactional` do login) iria parar no banco se fosse
 * alterado.
 */
class UsuarioDetails(val usuario: Usuario) : User(usuario.email, usuario.senhaHash, emptyList())
