package br.com.tavaressan.cafey.mail

/**
 * Abstração do transporte de e-mail (BE-23, issue #105). A implementação ativa é escolhida por
 * perfil Spring: [LogEmailSenderService] em desenvolvimento/teste e [ProdEmailSenderService]
 * (AWS SES via SMTP, issue #194) em produção.
 */
interface EmailSenderService {
    /**
     * Envia o e-mail de recuperação de senha (UC-03) contendo o link de redefinição com o token
     * em texto claro. O token nunca deve ser exposto pela API — este é o único canal de entrega.
     */
    fun enviarEmailRecuperacaoSenha(destinatario: String, token: String)
}
