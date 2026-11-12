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

        // Depois da primeira leitura, mesmo evento não deve gerar nova notificação
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
        detector.onPoll(NotificationPollSnapshot(accountKey = "user-1", devices = listOf(device)))

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

        // online -> offline: notifica
        val off = detector.onPoll(
            NotificationPollSnapshot(accountKey = "user-1", devices = listOf(device.copy(online = false))),
        )
        assertEquals(1, off.size)
        assertEquals(DesktopNotificationType.DISPOSITIVO_OFFLINE, off[0].type)
        assertEquals("A cafeteira ficou offline", off[0].body)

        // permanece offline: não notifica de novo
        val stillOff = detector.onPoll(
            NotificationPollSnapshot(accountKey = "user-1", devices = listOf(device.copy(online = false))),
        )
        assertTrue(stillOff.isEmpty())

        // offline -> online: não notifica
        val backOnline = detector.onPoll(
            NotificationPollSnapshot(accountKey = "user-1", devices = listOf(device.copy(online = true))),
        )
        assertTrue(backOnline.isEmpty())

        // nova transição online -> offline: notifica novamente
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
                recentEventosByDevice = mapOf("cafey-1" to listOf(evt)),
            ),
        )
        // Consome evento sob user-1
        detector.onPoll(
            NotificationPollSnapshot(
                accountKey = "user-1",
                devices = listOf(device.copy(state = DeviceState.Ready)),
                recentEventosByDevice = mapOf("cafey-1" to listOf(evt)),
            ),
        )

        // Troca de conta: primeira leitura não notifica histórico
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
        detector.onPoll(NotificationPollSnapshot(accountKey = "user-1", devices = listOf(device)))

        val started = detector.onPoll(
            NotificationPollSnapshot(accountKey = "user-1", devices = listOf(device.copy(state = DeviceState.Brewing))),
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
    }
}
