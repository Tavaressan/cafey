package br.com.tavaressan.cafey.mail

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Stub do envio real de e-mail em produção. A escolha do provedor (AWS SES vs. SMTP genérico),
 * identidade do remetente e demais decisões de Parte B da issue #105 estão bloqueadas aguardando
 * o dono do projeto — ver comentário da issue. Enquanto isso, o perfil `prod` sobe normalmente
 * (sem esta implementação, o contexto Spring falharia ao iniciar por falta de um bean de
 * [EmailSenderService]), mas qualquer solicitação de recuperação de senha falha de forma
 * controlada: o erro é apenas logado por [br.com.tavaressan.cafey.auth.AuthService] (nunca vira
 * 5xx nem muda a resposta da API, para não abrir oráculo de enumeração de contas).
 */
@Component
@Profile("prod")
class ProdEmailSenderService : EmailSenderService {

    override fun enviarEmailRecuperacaoSenha(destinatario: String, token: String) {
        throw UnsupportedOperationException(
            "Envio real de e-mail ainda não implementado (issue #105, Parte B bloqueada " +
                "aguardando decisão do provedor SES/SMTP)."
        )
    }
}
