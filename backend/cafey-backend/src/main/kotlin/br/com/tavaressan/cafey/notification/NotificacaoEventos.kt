package br.com.tavaressan.cafey.notification

import java.time.Instant
import java.util.UUID

// Eventos de domínio publicados pela ingestão (MQTT) e consumidos por [NotificacaoListener] depois do
// commit. Ficam neste pacote para a ingestão não depender de como as notificações são entregues.

/** Um evento de preparo novo foi gravado (já deduplicado por `eventoId`, #286). */
data class EventoPreparoRegistradoEvent(
    val dispositivoId: UUID,
    val eventoId: String,
    val resultado: String,
    val timestamp: Instant,
    val detalheErro: String?
)

/** O estado da base passou de "parado" para "preparando". */
data class PreparoIniciadoEvent(val dispositivoId: UUID)

/** A base passou de online para offline (normalmente o Last Will do broker). */
data class DispositivoOfflineEvent(val dispositivoId: UUID, val ultimoVisto: Instant)
