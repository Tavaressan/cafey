package br.com.tavaressan.cafey.shared.auth

/**
 * Usuário admin fixo para o período de testes — permite entrar no app sem depender de um
 * backend/registro reais. Reverter (remover este arquivo e o uso em [LoginViewModel]) antes de
 * qualquer deploy fora deste período de testes: não há verificação real de credenciais.
 */
object DebugAdminCredentials {
    const val EMAIL = "admin@cafey.com"
    const val SENHA = "admin123"

    fun matches(email: String, senha: String): Boolean = email == EMAIL && senha == SENHA
}
