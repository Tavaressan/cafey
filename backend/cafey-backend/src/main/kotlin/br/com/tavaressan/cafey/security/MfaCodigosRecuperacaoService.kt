package br.com.tavaressan.cafey.security

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.HexFormat
import java.util.UUID

/**
 * Códigos de recuperação do 2FA: permitem entrar ou desativar o 2FA sem o app autenticador.
 *
 * Cada código tem 10 caracteres de um alfabeto sem símbolos ambíguos (sem 0/O, 1/I/L), cerca de
 * 49 bits de entropia, e é exibido como `XXXXX-XXXXX`. Só o hash SHA-256 vai para o banco; o texto
 * aparece uma única vez, na resposta da ativação.
 */
@Service
class MfaCodigosRecuperacaoService(
    private val repository: MfaCodigoRecuperacaoRepository
) {
    private val random = SecureRandom()

    /** Apaga os códigos anteriores e gera um conjunto novo. Devolve os códigos em texto. */
    @Transactional
    fun gerarNovos(usuarioId: UUID): List<String> {
        repository.removerTodos(usuarioId)
        val codigos = generateSequence { novoCodigo() }.distinct().take(QUANTIDADE).toList()
        repository.saveAll(codigos.map { MfaCodigoRecuperacao(usuarioId = usuarioId, codigoHash = hash(it)) })
        return codigos.map { "${it.substring(0, 5)}-${it.substring(5)}" }
    }

    /** Consome o código se ele for válido. Cada código funciona uma única vez. */
    @Transactional
    fun consumir(usuarioId: UUID, codigo: String): Boolean {
        val normalizado = normalizar(codigo) ?: return false
        return repository.consumir(usuarioId, hash(normalizado)) > 0
    }

    @Transactional(readOnly = true)
    fun restantes(usuarioId: UUID): Int = repository.countByUsuarioId(usuarioId).toInt()

    @Transactional
    fun removerTodos(usuarioId: UUID) {
        repository.removerTodos(usuarioId)
    }

    private fun novoCodigo(): String =
        String(CharArray(TAMANHO) { ALFABETO[random.nextInt(ALFABETO.length)] })

    private fun hash(codigo: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(codigo.toByteArray(Charsets.UTF_8)))

    companion object {
        const val QUANTIDADE = 10
        private const val TAMANHO = 10
        private const val ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

        /** Aceita maiúsculas/minúsculas, com ou sem hífen e espaços. Devolve null se o formato não bate. */
        fun normalizar(codigo: String): String? {
            val limpo = codigo.uppercase().filterNot { it == '-' || it.isWhitespace() }
            return limpo.takeIf { it.length == TAMANHO && it.all { c -> c in ALFABETO } }
        }
    }
}
