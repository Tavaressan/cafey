package br.com.tavaressan.cafey.mail

/**
 * Fake de teste para [EmailSenderService]: evita o problema de matchers do Mockito com parâmetros
 * `String` não anuláveis (`eq`/`any` retornam `null` para tipos de referência em Kotlin) e permite
 * inspecionar exatamente qual destinatário/token foi entregue.
 */
class FakeEmailSenderService : EmailSenderService {
    var chamadas = 0
        private set
    var ultimoDestinatario: String? = null
        private set
    var ultimoToken: String? = null
        private set

    override fun enviarEmailRecuperacaoSenha(destinatario: String, token: String) {
        chamadas++
        ultimoDestinatario = destinatario
        ultimoToken = token
    }
}
