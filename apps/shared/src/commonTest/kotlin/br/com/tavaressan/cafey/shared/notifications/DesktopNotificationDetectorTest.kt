package br.com.tavaressan.cafey.shared.notifications

import br.com.tavaressan.cafey.shared.domain.model.DeviceState
import br.com.tavaressan.cafey.shared.domain.model.EventoResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopNotificationDetectorTest {
    private val device = DeviceSnapshot(
        id = "cafey-1",
        nome = "Caféy Cozinha",
        online = true,
        state = DeviceState.Idle,
    )

    @Test
    fun firstRead_doesNotNotifyHistory() {
        val detector = DesktopNotificationDetector()
        val events = listOf(
            EventoResponse(
                id = "e1",
                eventoId = "evt-1",
                tipo = "PREPARO",
                resultado = "CONCLUIDO",
                origem = "APP",
                duracaoS = 30,
                timestamp = "2026-11-12T10:00:00Z",
                criadoEm = "2026-11-12T10:00:01Z",
            ),
        )
        val first = detector.onPoll(
            NotificationPollSnapshot(
                accountKey = "user-1",
                devices = listOf(device),
                recentEventosByDevice = mapOf("cafey-1" to events),
            ),
        )
        assertTrue(first.isEmpty())

        val second = detector.onPoll(
            NotificationPollSnapshot(
                accountKey = "user-1",
                devices = listOf(device.copy(state = DeviceState.Idle)),
                recentEventosByDevice = mapOf("cafey-1" to events),
            ),
        )
        assertTrue(second.isEmpty())
    }

    @Test
    fun duplicateByEventoId_isDeduplicated() {
        val detector = DesktopNotificationDetector()
        detector.onPoll(
            NotificationPollSnapshot(
                accountKey = "user-1",
                devices = listOf(device),
                recentEventosByDevice = mapOf("cafey-1" to emptyList()),
            ),
        )

        val evt = EventoResponse(
            id = "e1",
            eventoId = "evt-dup",
            tipo = "PREPARO",
            resultado = "CONCLUIDO",
            origem = "APP",
            duracaoS = 30,
            timestamp = "2026-11-12T10:00:00Z",
            criadoEm = "2026-11-12T10:00:01Z",
        )

        val first = detector.onPoll(
            NotificationPollSnapshot(
                accountKey = "user-1",
                devices = listOf(device.copy(state = DeviceState.Ready)),
                recentEventosByDevice = mapOf("cafey-1" to listOf(evt)),
            ),
        )
        assertEquals(1, first.size)
        assertEquals(DesktopNotificationType.CAFE_PRONTO, first[0].type)
        assertEquals("Seu café está pronto! Enjoy!", first[0].body)
        assertEquals("Caféy Cozinha", first[0].title)
        assertEquals("evt-dup", first[0].eventoId)

        val second = detector.onPoll(
            NotificationPollSnapshot(
                accountKey = "user-1",
                devices = listOf(device.copy(state = DeviceState.Idle)),
                recentEventosByDevice = mapOf("cafey-1" to listOf(evt)),
            ),
        )
        assertTrue(second.isEmpty())
    }

    @Test
    fun offline_onlyOnTransition_onlineToOffline() {
        val detector = DesktopNotificationDetector()
        detector.onPoll(NotificationPollSnapshot(accountKey = "user-1", devices = listOf(device)))

        val off = detector.onPoll(
            NotificationPollSnapshot(accountKey = "user-1", devices = listOf(device.copy(online = false))),
        )
        assertEquals(1, off.size)
        assertEquals(DesktopNotificationType.DISPOSITIVO_OFFLINE, off[0].type)
        assertEquals("A cafeteira ficou offline", off[0].body)

        val stillOff = detector.onPoll(
            NotificationPollSnapshot(accountKey = "user-1", devices = listOf(device.copy(online = false))),
        )
        assertTrue(stillOff.isEmpty())

        val backOnline = detector.onPoll(
            NotificationPollSnapshot(accountKey = "user-1", devices = listOf(device.copy(online = true))),
        )
        assertTrue(backOnline.isEmpty())

        val offAgain = detector.onPoll(
            NotificationPollSnapshot(accountKey = "user-1", devices = listOf(device.copy(online = false))),
        )
        assertEquals(1, offAgain.size)
        assertEquals(DesktopNotificationType.DISPOSITIVO_OFFLINE, offAgain[0].type)
    }

    @Test
    fun accountSwitch_resetsState() {
        val detector = DesktopNotificationDetector()
        val evt = EventoResponse(
            id = "e1",
            eventoId = "evt-acc",
            tipo = "PREPARO",
            resultado = "CONCLUIDO",
            origem = "APP",
            duracaoS = 30,
            timestamp = "2026-11-12T10:00:00Z",
            criadoEm = "2026-11-12T10:00:01Z",
        )
        detector.onPoll(
            NotificationPollSnapshot(
                accountKey = "user-1",
                devices = listOf(device),
                recentEventosByDevice = mapOf("cafey-1" to emptyList()),
            ),
        )
        detector.onPoll(
            NotificationPollSnapshot(
                accountKey = "user-1",
                devices = listOf(device.copy(state = DeviceState.Ready)),
                recentEventosByDevice = mapOf("cafey-1" to listOf(evt)),
            ),
        )

        val afterSwitch = detector.onPoll(
            NotificationPollSnapshot(
                accountKey = "user-2",
                devices = listOf(device.copy(state = DeviceState.Idle)),
                recentEventosByDevice = mapOf("cafey-1" to listOf(evt)),
            ),
        )
        assertTrue(afterSwitch.isEmpty())
    }

    @Test
    fun preparaIniciado_andFalhaPorEvento() {
        val detector = DesktopNotificationDetector()
        detector.onPoll(
            NotificationPollSnapshot(
                accountKey = "user-1",
                devices = listOf(device),
                recentEventosByDevice = mapOf("cafey-1" to emptyList()),
            ),
        )

        val started = detector.onPoll(
            NotificationPollSnapshot(
                accountKey = "user-1",
                devices = listOf(device.copy(state = DeviceState.Brewing)),
                recentEventosByDevice = mapOf("cafey-1" to emptyList()),
            ),
        )
        assertEquals(1, started.size)
        assertEquals(DesktopNotificationType.PREPARO_INICIADO, started[0].type)
        assertEquals("Preparando seu café…", started[0].body)

        val erroEvt = EventoResponse(
            id = "e2",
            eventoId = "evt-err",
            tipo = "PREPARO",
            resultado = "ERRO",
            origem = "BOTAO",
            duracaoS = 0,
            timestamp = "2026-11-12T10:05:00Z",
            detalheErro = "Nível baixo",
            criadoEm = "2026-11-12T10:05:01Z",
        )
        val failed = detector.onPoll(
            NotificationPollSnapshot(
                accountKey = "user-1",
                devices = listOf(device.copy(state = DeviceState.Error)),
                recentEventosByDevice = mapOf("cafey-1" to listOf(erroEvt)),
            ),
        )
        assertEquals(1, failed.size)
        assertEquals(DesktopNotificationType.FALHA_PREPARO, failed[0].type)
        assertTrue(failed[0].body.contains("Não foi possível preparar o café"))
        assertEquals("evt-err", failed[0].eventoId)
    }

    @Test
    fun estadoAntesDoEvento_naoDuplica_paraDispositivoComEventosBuscados() {
        val detector = DesktopNotificationDetector()
        detector.onPoll(
            NotificationPollSnapshot(
                accountKey = "user-1",
                devices = listOf(device.copy(state = DeviceState.Brewing)),
                recentEventosByDevice = mapOf("cafey-1" to emptyList()),
            ),
        )

        // Estado muda para PRONTO mas evento ainda não chegou: como eventos são buscados, não usa fallback.
        val noEventYet = detector.onPoll(
            NotificationPollSnapshot(
                accountKey = "user-1",
                devices = listOf(device.copy(state = DeviceState.Ready)),
                recentEventosByDevice = mapOf("cafey-1" to emptyList()),
            ),
        )
        assertTrue(noEventYet.isEmpty())

        val concluido = EventoResponse(
            id = "e3",
            eventoId = "evt-late",
            tipo = "PREPARO",
            resultado = "CONCLUIDO",
            origem = "APP",
            duracaoS = 30,
            timestamp = "2026-11-12T10:10:00Z",
            criadoEm = "2026-11-12T10:10:01Z",
        )
        val byEvent = detector.onPoll(
            NotificationPollSnapshot(
                accountKey = "user-1",
                devices = listOf(device.copy(state = DeviceState.Ready)),
                recentEventosByDevice = mapOf("cafey-1" to listOf(concluido)),
            ),
        )
        assertEquals(1, byEvent.size)
        assertEquals(DesktopNotificationType.CAFE_PRONTO, byEvent[0].type)
        assertEquals("evt-late", byEvent[0].eventoId)

        // Leitura seguinte com mesmo evento não duplica.
        val again = detector.onPoll(
            NotificationPollSnapshot(
                accountKey = "user-1",
                devices = listOf(device.copy(state = DeviceState.Ready)),
                recentEventosByDevice = mapOf("cafey-1" to listOf(concluido)),
            ),
        )
        assertTrue(again.isEmpty())
    }

    @Test
    fun fallbackPorEstado_apenasParaDispositivosSemEventosBuscados() {
        val detector = DesktopNotificationDetector()
        val other = DeviceSnapshot(id = "cafey-2", nome = "Caféy Sala", online = true, state = DeviceState.Idle)
        detector.onPoll(NotificationPollSnapshot(accountKey = "user-1", devices = listOf(device, other)))

        val emitted = detector.onPoll(
            NotificationPollSnapshot(
                accountKey = "user-1",
                devices = listOf(device, other.copy(state = DeviceState.Ready)),
            ),
        )
        assertEquals(1, emitted.size)
        assertEquals(DesktopNotificationType.CAFE_PRONTO, emitted[0].type)
        assertEquals("cafey-2", emitted[0].deviceId)
        assertEquals(null, emitted[0].eventoId)
    }

    @Test
    fun trocaDeDispositivoSelecionado_primaEventosDoNovoSemNotificarHistorico() {
        val detector = DesktopNotificationDetector()
        val selected = device
        val other = DeviceSnapshot(id = "cafey-2", nome = "Caféy Sala", online = true, state = DeviceState.Idle)

        val hist1 = EventoResponse("e1", "evt-old-1", "PREPARO", "CONCLUIDO", "APP", 30, "2026-11-12T10:00:00Z", null, "2026-11-12T10:00:01Z")
        val hist2 = EventoResponse("e2", "evt-old-2", "PREPARO", "CONCLUIDO", "APP", 30, "2026-11-12T09:00:00Z", null, "2026-11-12T09:00:01Z")

        // Primeira carga: selecionado é cafey-1.
        detector.onPoll(
            NotificationPollSnapshot(
                accountKey = "user-1",
                devices = listOf(selected, other),
                recentEventosByDevice = mapOf("cafey-1" to listOf(hist1)),
            ),
        )

        // Troca seleção para cafey-2: eventos históricos do novo selecionado devem ser primados.
        val onSwitch = detector.onPoll(
            NotificationPollSnapshot(
                accountKey = "user-1",
                devices = listOf(selected, other),
                recentEventosByDevice = mapOf("cafey-2" to listOf(hist2)),
            ),
        )
        assertTrue(onSwitch.isEmpty())

        // Evento novo no cafey-2 após priming deve notificar.
        val novo = EventoResponse("e3", "evt-new-2", "PREPARO", "CONCLUIDO", "APP", 30, "2026-11-12T11:00:00Z", null, "2026-11-12T11:00:01Z")
        val after = detector.onPoll(
            NotificationPollSnapshot(
                accountKey = "user-1",
                devices = listOf(selected, other),
                recentEventosByDevice = mapOf("cafey-2" to listOf(novo, hist2)),
            ),
        )
        assertEquals(1, after.size)
        assertEquals("cafey-2", after[0].deviceId)
        assertEquals("evt-new-2", after[0].eventoId)
    }
}
