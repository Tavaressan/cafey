package br.com.tavaressan.cafey.notification

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuração das notificações push (épico #318). Estrutura plana, no mesmo espírito de
 * [br.com.tavaressan.cafey.config.RateLimitProperties].
 *
 * Sem credenciais o boot segue normal: o canal sem configuração fica desligado e só registra em log
 * que ignorou o envio (ambiente local e testes).
 */
@ConfigurationProperties(prefix = "cafey.push")
class NotificacaoProperties(
    /** Tempo que a base precisa ficar offline antes de avisar (o Last Will oscila em quedas curtas). */
    var offlineDebounceSegundos: Long = 180,
    /** Evento de preparo mais antigo que isto (fila do firmware esvaziada depois) não vira push. */
    var janelaEventoMinutos: Long = 15,
    var maxRegistrosPorUsuario: Int = 10,
    /** Registros sem re-registro do app por mais que isto são apagados. */
    var expiracaoRegistroDias: Long = 60,
    /** Tentativas por destino em falhas transitórias (inclui a primeira). */
    var tentativas: Int = 3,
    var backoffInicialMs: Long = 1000,

    /** Caminho do JSON da conta de serviço do Firebase (#362). Vazio desliga o canal Android. */
    var fcmCredenciaisPath: String = "",
    var fcmBaseUrl: String = "https://fcm.googleapis.com",

    /** Par VAPID em Base64URL (#363): pública = ponto P-256 não comprimido (65 bytes); privada = escalar (32 bytes). */
    var vapidPublicKey: String = "",
    var vapidPrivateKey: String = "",
    /** `mailto:` ou URL de contato exigido pelos serviços de push (RFC 8292). */
    var vapidSubject: String = "",
    /**
     * Hosts aceitos como endpoint de assinatura Web Push. O endpoint vem do cliente: sem esta lista
     * o backend faria POST para qualquer URL (SSRF). Entrada iniciada por "." aceita subdomínios.
     */
    var webpushHostsPermitidos: List<String> = listOf(
        "fcm.googleapis.com",
        "updates.push.services.mozilla.com",
        ".notify.windows.com",
        "web.push.apple.com"
    ),
    /** Só para testes com servidor local: aceita endpoint http. */
    var webpushPermitirHttp: Boolean = false
)
