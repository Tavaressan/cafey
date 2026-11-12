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
 * Dados alimentados a cada ciclo de polling. [recentEventosByDevice] contém eventos recentes de
 * preparo para dispositivos cujos eventos foram efetivamente consultados (ex.: dispositivo
 * selecionado). A presença da chave indica fonte autoritativa por eventos para pronto/falha.
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
 * Detector puro de transições para notificações locais do desktop. Não depende de UI.
 *
 * Regras:
 * - Primeira observação (app abre/troca de conta/troca para dispositivo com eventos buscados):
 *   registra estado e eventos conhecidos, sem emitir nada.
 * - [DesktopNotificationType.CAFE_PRONTO] / [FALHA_PREPARO]: para dispositivos com eventos
 *   buscados, apenas por eventos CONCLUIDO/ERRO novos (deduplicados por [EventoResponse.eventoId]);
 *   para os demais, por transição de estado para pronto/erro.
 * - [DesktopNotificationType.PREPARO_INICIADO]: transição para PREPARANDO.
 * - [DesktopNotificationType.DISPOSITIVO_OFFLINE]: apenas transição online -> offline.
 */
class DesktopNotificationDetector {
    private var currentAccountKey: String? = null
    private val observations = mutableMapOf<String, DeviceObservation>()
    private val seenEventoIds = mutableSetOf<String>()
    private val eventsPrimedDevices = mutableSetOf<String>()

    /** Zera o estado interno. A próxima leitura não notifica. */
    fun reset() {
        currentAccountKey = null
        observations.clear()
        seenEventoIds.clear()
        eventsPrimedDevices.clear()
    }

    fun onPoll(snapshot: NotificationPollSnapshot): List<DesktopNotificationEvent> {
        if (snapshot.accountKey != currentAccountKey) {
            currentAccountKey = snapshot.accountKey
            observations.clear()
            seenEventoIds.clear()
            eventsPrimedDevices.clear()
            prime(snapshot)
            return emptyList()
        }

        val emitted = mutableListOf<DesktopNotificationEvent>()
        val activeIds = snapshot.devices.map { it.id }.toSet()
        observations.keys.retainAll(activeIds)
        eventsPrimedDevices.retainAll(snapshot.recentEventosByDevice.keys)

        for (device in snapshot.devices) {
            val previous = observations[device.id]
            val eventosFetched = snapshot.recentEventosByDevice.containsKey(device.id)
            val eventos = snapshot.recentEventosByDevice[device.id].orEmpty()

            // Primeira observação do dispositivo ou primeira vez com eventos buscados: não notifica histórico.
            if (previous == null || !previous.initialized) {
                observations[device.id] = DeviceObservation(device.online, device.state, initialized = true)
                if (eventosFetched) {
                    primeEventos(eventos)
                    eventsPrimedDevices += device.id
                }
                continue
            }
            if (eventosFetched && device.id !in eventsPrimedDevices) {
                primeEventos(eventos)
                eventsPrimedDevices += device.id
                observations[device.id] = DeviceObservation(device.online, device.state, initialized = true)
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

            if (eventosFetched) {
                // Dispositivo com eventos buscados: somente eventos determinam pronto/falha.
                val (eventoConcluido, eventoErro) = detectOutcomeEventos(eventos)
                if (eventoConcluido != null) {
                    emitted += notification(
                        type = DesktopNotificationType.CAFE_PRONTO,
                        device = device,
                        eventoId = eventoConcluido.eventoId,
                    )
                }
                if (eventoErro != null) {
                    emitted += notification(
                        type = DesktopNotificationType.FALHA_PREPARO,
                        device = device,
                        eventoId = eventoErro.eventoId,
                        detalhe = eventoErro.detalheErro,
                    )
                }
            } else {
                // Demais dispositivos: fallback por transição de estado.
                if (previous.state != DeviceState.Ready && device.state == DeviceState.Ready) {
                    emitted += notification(
                        type = DesktopNotificationType.CAFE_PRONTO,
                        device = device,
                        eventoId = null,
                    )
                }
                if (previous.state != DeviceState.Error && device.state == DeviceState.Error) {
                    emitted += notification(
                        type = DesktopNotificationType.FALHA_PREPARO,
                        device = device,
                        eventoId = null,
                    )
                }
            }

            observations[device.id] = DeviceObservation(device.online, device.state, initialized = true)
        }

        return emitted
    }

    private fun prime(snapshot: NotificationPollSnapshot) {
        for (device in snapshot.devices) {
            observations[device.id] = DeviceObservation(device.online, device.state, initialized = true)
            if (snapshot.recentEventosByDevice.containsKey(device.id)) {
                eventsPrimedDevices += device.id
            }
        }
        for ((_, eventos) in snapshot.recentEventosByDevice) {
            primeEventos(eventos)
        }
    }

    private fun primeEventos(eventos: List<EventoResponse>) {
        for (e in eventos) {
            seenEventoIds += e.eventoId
        }
    }

    private fun detectOutcomeEventos(eventos: List<EventoResponse>): Pair<EventoResponse?, EventoResponse?> {
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
                    seenEventoIds += e.eventoId
                }
            }
        }
        return concluido to erro
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
