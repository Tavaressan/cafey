package br.com.tavaressan.cafey.mail

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Implementação de desenvolvimento/teste do envio de e-mail (BE-23, issue #105): apenas registra
 * o link de redefinição de senha no log, sem SMTP/SES real. Ativa em qualquer perfil que não seja
 * `prod` — o perfil `prod` usa a implementação real (bloqueada em #105 até decisão do provedor).
 */
@Component
@Profile("!prod")
class LogEmailSenderService(
    private val properties: EmailProperties
) : EmailSenderService {

    private val logger = LoggerFactory.getLogger(LogEmailSenderService::class.java)

    override fun enviarEmailRecuperacaoSenha(destinatario: String, token: String) {
        val link = "${properties.resetPasswordUrl}?token=$token"
        logger.info("[email simulado] recuperação de senha para {}: {}", destinatario, link)
    }
}
