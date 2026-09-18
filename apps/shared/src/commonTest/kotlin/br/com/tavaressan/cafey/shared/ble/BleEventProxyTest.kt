package br.com.tavaressan.cafey.shared.ble

import br.com.tavaressan.cafey.shared.domain.model.BleEvento
import br.com.tavaressan.cafey.shared.domain.model.EventoResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val DEVICE_ID = "device-1"

/** Dublê de [EventoProxyRemoto] — devolve um [EventoResponse] por evento recebido. */
private class FakeEventoProxyRemoto : EventoProxyRemoto {
    val recebidos = mutableListOf<Pair<String, List<BleEvento>>>()

    override suspend fun enviarProxyBle(dispositivoId: String, eventos: List<BleEvento>): List<EventoResponse> {
        recebidos += dispositivoId to eventos
        return eventos.map {
            EventoResponse(
                id = "srv-${it.eventoId}",
                eventoId = it.eventoId,
                tipo = it.tipo,
                resultado = it.resultado,
                origem = it.origem,
                duracaoS = it.duracaoS,
                timestamp = it.timestamp,
                detalheErro = it.detalheErro,
                criadoEm = "2026-01-01T00:00:00Z",
            )
        }
    }
}

/** Reproduz o proxy BLE → nuvem (spec §6.5): lê a fila local, repassa ao backend e só então
 * confirma a entrega para a base limpar a fila. */
class BleEventProxyTest {

    @Test
    fun filaComEventos_enviaERconfirmaNaBle() = runTest {
        val evento = BleEvento(
            eventoId = "evt-1",
            resultado = "CONCLUIDO",
            origem = "BOTAO",
            duracaoS = 25,
            timestamp = "2026-01-01T10:00:00Z",
        )
        val bleClient = FakeBleClient(eventosPendentes = listOf(evento))
        val eventApi = FakeEventoProxyRemoto()
        val proxy = BleEventProxy(eventApi, bleClient)

        val resultado = proxy.sincronizar(DEVICE_ID)

        assertEquals(1, resultado.size)
        assertEquals("evt-1", resultado.first().eventoId)
        assertEquals(listOf(DEVICE_ID to listOf(evento)), eventApi.recebidos)
        assertEquals(listOf(DEVICE_ID to listOf("evt-1")), bleClient.confirmacoes)
    }

    @Test
    fun filaVazia_naoChamaBackendNemConfirma() = runTest {
        val bleClient = FakeBleClient(eventosPendentes = emptyList())
        val eventApi = FakeEventoProxyRemoto()
        val proxy = BleEventProxy(eventApi, bleClient)

        val resultado = proxy.sincronizar(DEVICE_ID)

        assertTrue(resultado.isEmpty())
        assertTrue(eventApi.recebidos.isEmpty())
        assertTrue(bleClient.confirmacoes.isEmpty())
    }
}
