package br.com.tavaressan.cafey.shared.ui.auth

/**
 * Formatos aceitos como segundo fator: o código TOTP de 6 dígitos do app autenticador ou um código de
 * recuperação (`XXXXX-XXXXX`, aceita minúsculas e sem hífen). Espelha a regra do backend.
 */
object CodigoSegundoFator {
    private const val ALFABETO_RECUPERACAO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    fun isTotp(codigo: String): Boolean = codigo.length == 6 && codigo.all { it.isDigit() }

    fun isRecuperacao(codigo: String): Boolean {
        val limpo = codigo.uppercase().filterNot { it == '-' || it.isWhitespace() }
        return limpo.length == 10 && limpo.all { it in ALFABETO_RECUPERACAO }
    }

    fun isValido(codigo: String): Boolean = isTotp(codigo.trim()) || isRecuperacao(codigo)

    fun filtrarTotp(valor: String): String = valor.filter { it.isDigit() }.take(6)

    /** Campo que aceita os dois formatos: letras, dígitos e hífen, em maiúsculas, até 11 caracteres. */
    fun filtrarQualquer(valor: String): String =
        valor.uppercase().filter { (it in 'A'..'Z') || it.isDigit() || it == '-' }.take(11)
}
