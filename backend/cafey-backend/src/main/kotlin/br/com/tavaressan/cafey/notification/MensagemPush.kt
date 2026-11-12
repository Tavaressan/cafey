package br.com.tavaressan.cafey.notification

import java.time.Duration
import java.util.UUID

/**
 * Tipos de notificação (#361). `canalAndroid` casa com os canais criados pelo app (#364);
 * `ttl` limita por quanto tempo o serviço de push guarda a mensagem se o aparelho estiver offline.
 */
enum class TipoNotificacao(val canalAndroid: String, val ttl: Duration, val urgente: Boolean) {
    PREPARO_INICIADO("status", Duration.ofMinutes(5), false),
    CAFE_PRONTO("preparo", Duration.ofHours(1), true),
    FALHA_PREPARO("preparo", Duration.ofHours(1), true),
    DISPOSITIVO_OFFLINE("status", Duration.ofHours(6), false),
    TESTE("status", Duration.ofMinutes(5), false)
}

/**
 * Conteúdo de uma notificação, igual para todos os canais. Não carrega token, e-mail nem outro dado
 * sensível: só o que a notificação mostra e o que o app precisa para abrir a tela certa.
 */
data class MensagemPush(
    val tipo: TipoNotificacao,
    val titulo: String,
    val corpo: String,
    val dispositivoId: UUID? = null,
    val eventoId: String? = null
) {
    /** Agrupa notificações do mesmo tipo e dispositivo: a nova substitui a anterior na bandeja. */
    val tag: String get() = listOfNotNull(tipo.name, dispositivoId?.toString()).joinToString(":")

    fun dados(): Map<String, String> = buildMap {
        put("tipo", tipo.name)
        put("titulo", titulo)
        put("corpo", corpo)
        put("tag", tag)
        dispositivoId?.let { put("dispositivoId", it.toString()) }
        eventoId?.let { put("eventoId", it) }
    }
}
