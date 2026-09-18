package br.com.tavaressan.cafey.mail

/**
 * Abstração do transporte de e-mail (BE-23, issue #105). A implementação ativa é escolhida por
 * perfil Spring: [LogEmailSenderService] em desenvolvimento, uma implementação real em produção
 * (bloqueada em #105 aguardando decisão do provedor — SES vs. SMTP).
 */
interface EmailSenderService {
    /**
     * Envia o e-mail de recuperação de senha (UC-03) contendo o link de redefinição com o token
     * em texto claro. O token nunca deve ser exposto pela API — este é o único canal de entrega.
     */
    fun enviarEmailRecuperacaoSenha(destinatario: String, token: String)
}
