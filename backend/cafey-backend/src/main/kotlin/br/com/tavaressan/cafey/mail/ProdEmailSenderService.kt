package br.com.tavaressan.cafey.mail

import org.springframework.context.annotation.Profile
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component

/**
 * Envio real de e-mail em produção (issue #194, Parte B de #105) via SMTP do AWS SES, usando o
 * [JavaMailSender] configurado em `spring.mail.*` (`application-prod.yml`, valores vindos de
 * variáveis de ambiente `CAFEY_MAIL_SMTP_*`).
 *
 * Falhas de envio (SMTP indisponível, credenciais inválidas, destinatário fora do sandbox do SES)
 * são propagadas de propósito: quem chama, [br.com.tavaressan.cafey.auth.AuthService], apenas as
 * registra em log — nunca viram 5xx nem mudam a resposta da API (evita oráculo de enumeração de
 * contas).
 */
@Component
@Profile("prod")
class ProdEmailSenderService(
    private val mailSender: JavaMailSender,
    private val properties: EmailProperties
) : EmailSenderService {

    override fun enviarEmailRecuperacaoSenha(destinatario: String, token: String) {
        val link = "${properties.resetPasswordUrl}?token=$token"
        val message = SimpleMailMessage().apply {
            from = properties.from
            setTo(destinatario)
            subject = "Cafey - redefinição de senha"
            text = "Recebemos uma solicitação para redefinir a senha da sua conta Cafey.\n\n" +
                "Para escolher uma nova senha, acesse o link abaixo:\n$link\n\n" +
                "Se você não fez essa solicitação, ignore este e-mail."
        }
        mailSender.send(message)
    }
}
