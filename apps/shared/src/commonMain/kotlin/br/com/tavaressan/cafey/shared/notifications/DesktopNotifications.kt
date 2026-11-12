package br.com.tavaressan.cafey.shared.notifications

import br.com.tavaressan.cafey.shared.domain.model.DeviceState
import br.com.tavaressan.cafey.shared.domain.model.EventoResponse

/** Tipos de notificação local do desktop (contrato alinhado ao push). */
enum class DesktopNotificationType {
    CAFE_PRONTO,
    FALHA_PREPARO,
    PREPARO_INICIADO,
    DISPOSITIVO_OFFLINE,
}

data class DesktopNotificationEvent(
    val type: DesktopNotificationType,
    /** Nome da cafeteira. */
    val title: String,
    val body: String,
    /** Identificador do dispositivo (para focar/selecionar na UI). */
    val deviceId: String,
    /** Evento de preparo associado, quando houver (para deduplicação/diagnóstico). */
    val eventoId: String? = null,
    /** Tag estável no formato do push (`TIPO:<dispositivoId>` ou `TIPO:<eventoId>`). */
    val tag: String,
)

/** Snapshot mínimo de um dispositivo observado pelo polling. */
data class DeviceSnapshot(
    val id: String,
    val nome: String,
    val online: Boolean,
    val state: DeviceState,
)

/**
 * Dados alimentados a cada ciclo de polling. [recentEventos] traz eventos de preparo recentes
 * (por exemplo, da página mais nova) para detectar conclusões/falhas por [EventoResponse.eventoId].
 * Quando vazio, apenas transições de estado/online são detectadas.
 */
data class NotificationPollSnapshot(
    /** Identificador lógico da conta/sessão atual; ao mudar, o histórico é zerado sem notificar. */
    val accountKey: String?,
    val devices: List<DeviceSnapshot>,
    val recentEventosByDevice: Map<String, List<EventoResponse>> = emptyMap(),
)

private data class DeviceObservation(
    val online: Boolean,
    val state: DeviceState,
    val initialized: Boolean,
)

/**
 * Detector puro de transições para notificações locais do desktop. Não depende de UI e pode ser
 * reaproveitado em outros clientes que façam polling.
 *
 * Regras:
 * - Primeira observação (app abre ou troca de conta): registra o estado, sem emitir nada.
 * - [DesktopNotificationType.CAFE_PRONTO] / [FALHA_PREPARO]: transição para pronto/erro ou novo
 *   evento de preparo CONCLUIDO/ERRO observado após a inicialização; deduplicado por [eventoId].
 * - [DesktopNotificationType.PREPARO_INICIADO]: transição para PREPARANDO.
 * - [DesktopNotificationType.DISPOSITIVO_OFFLINE]: apenas transição online -> offline.
 */
class DesktopNotificationDetector {
    private var currentAccountKey: String? = null
    private val observations = mutableMapOf<String, DeviceObservation>()
    private val seenEventoIds = mutableSetOf<String>()

    /** Zera o estado interno (útil em logout/troca explícita). A próxima leitura não notifica. */
    fun reset() {
        currentAccountKey = null
        observations.clear()
        seenEventoIds.clear()
    }

    fun onPoll(snapshot: NotificationPollSnapshot): List<DesktopNotificationEvent> {
        if (snapshot.accountKey != currentAccountKey) {
            currentAccountKey = snapshot.accountKey
            observations.clear()
            seenEventoIds.clear()
            prime(snapshot)
            return emptyList()
        }

        val emitted = mutableListOf<DesktopNotificationEvent>()
        val activeIds = snapshot.devices.map { it.id }.toSet()
        observations.keys.retainAll(activeIds)

        for (device in snapshot.devices) {
            val previous = observations[device.id]
            if (previous == null || !previous.initialized) {
                observations[device.id] = DeviceObservation(device.online, device.state, initialized = true)
                // Prime eventos conhecidos deste dispositivo para não notificar histórico recém-visto.
                primeEventos(snapshot.recentEventosByDevice[device.id].orEmpty())
                continue
            }

            // Offline: apenas transição online -> offline.
            if (previous.online && !device.online) {
                emitted += notification(
                    type = DesktopNotificationType.DISPOSITIVO_OFFLINE,
                    device = device,
                    eventoId = null,
                )
            }

            // Preparo iniciado: transição para PREPARANDO.
            if (previous.state != DeviceState.Brewing && device.state == DeviceState.Brewing) {
                emitted += notification(
                    type = DesktopNotificationType.PREPARO_INICIADO,
                    device = device,
                    eventoId = null,
                )
            }

            // Conclusão/falha por estado do dispositivo (fallback quando eventos não chegam).
            val stateBecameReady = previous.state != DeviceState.Ready && device.state == DeviceState.Ready
            val stateBecameError = previous.state != DeviceState.Error && device.state == DeviceState.Error

            // Conclusão/falha por eventos de preparo com eventoId (deduplicados).
            val eventos = snapshot.recentEventosByDevice[device.id].orEmpty()
            val (eventoConcluido, eventoErro) = detectOutcomeEventos(eventos)

            if (eventoConcluido != null) {
                emitted += notification(
                    type = DesktopNotificationType.CAFE_PRONTO,
                    device = device,
                    eventoId = eventoConcluido.eventoId,
                )
            } else if (stateBecameReady) {
                emitted += notification(
                    type = DesktopNotificationType.CAFE_PRONTO,
                    device = device,
                    eventoId = syntheticEventoId(device.id, "READY"),
                )
            }

            if (eventoErro != null) {
                emitted += notification(
                    type = DesktopNotificationType.FALHA_PREPARO,
                    device = device,
                    eventoId = eventoErro.eventoId,
                    detalhe = eventoErro.detalheErro,
                )
            } else if (stateBecameError) {
                emitted += notification(
                    type = DesktopNotificationType.FALHA_PREPARO,
                    device = device,
                    eventoId = syntheticEventoId(device.id, "ERROR"),
                )
            }

            observations[device.id] = DeviceObservation(device.online, device.state, initialized = true)
        }

        return emitted
    }

    private fun prime(snapshot: NotificationPollSnapshot) {
        for (device in snapshot.devices) {
            observations[device.id] = DeviceObservation(device.online, device.state, initialized = true)
        }
        for (eventos in snapshot.recentEventosByDevice.values) {
            primeEventos(eventos)
        }
    }

    private fun primeEventos(eventos: List<EventoResponse>) {
        for (e in eventos) {
            seenEventoIds += e.eventoId
        }
    }

    private fun detectOutcomeEventos(eventos: List<EventoResponse>): Pair<EventoResponse?, EventoResponse?> {
        // Considera apenas eventos ainda não vistos; pega o mais recente de cada resultado relevante.
        var concluido: EventoResponse? = null
        var erro: EventoResponse? = null
        for (e in eventos) {
            if (e.eventoId in seenEventoIds) continue
            when (e.resultado.uppercase()) {
                "CONCLUIDO" -> {
                    seenEventoIds += e.eventoId
                    if (concluido == null) concluido = e
                }
                "ERRO" -> {
                    seenEventoIds += e.eventoId
                    if (erro == null) erro = e
                }
                else -> {
                    // Marca como visto para não reprocessar ruído, mas não notifica outros resultados.
                    seenEventoIds += e.eventoId
                }
            }
        }
        return concluido to erro
    }

    private fun syntheticEventoId(deviceId: String, outcome: String): String {
        val key = "state:$deviceId:$outcome"
        // Para transições por estado sem evento real, deduplica por marcador estável desta transição
        // lógica enquanto o estado não voltar; o chamador só emite em transição, então um id fixo por
        // "perna" de transição evita repetição no mesmo ciclo, mas permitimos reemissão se o estado
        // oscilar (nova transição real). Usamos contador simples via set: se já visto, sufixa.
        return if (seenEventoIds.add(key)) key else "$key:${seenEventoIds.size}"
    }

    private fun notification(
        type: DesktopNotificationType,
        device: DeviceSnapshot,
        eventoId: String?,
        detalhe: String? = null,
    ): DesktopNotificationEvent {
        val body = when (type) {
            DesktopNotificationType.CAFE_PRONTO -> "Seu café está pronto! Enjoy!"
            DesktopNotificationType.FALHA_PREPARO ->
                detalhe?.takeIf { it.isNotBlank() }?.let { curto ->
                    "Não foi possível preparar o café: ${curto.take(60)}"
                } ?: "Não foi possível preparar o café"
            DesktopNotificationType.PREPARO_INICIADO -> "Preparando seu café…"
            DesktopNotificationType.DISPOSITIVO_OFFLINE -> "A cafeteira ficou offline"
        }
        val tagSuffix = eventoId ?: device.id
        return DesktopNotificationEvent(
            type = type,
            title = device.nome,
            body = body,
            deviceId = device.id,
            eventoId = eventoId,
            tag = "${type.name}:$tagSuffix",
        )
    }
}
