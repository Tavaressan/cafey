package br.com.tavaressan.cafey.shared.ble

import br.com.tavaressan.cafey.shared.domain.model.EventoResponse

/**
 * Proxy BLE → nuvem (spec §6.5): quando a base está sem internet mas o celular está conectado por
 * BLE, o app repassa os eventos pendentes da fila local ao backend em nome do dispositivo e só
 * então confirma a entrega, permitindo que o ESP32 limpe a fila.
 *
 * A deduplicação de eventos atrasados (chave dispositivo + timestamp de início) é responsabilidade
 * do backend (`POST /dispositivos/{id}/eventos/proxy-ble`) — este proxy não reimplementa essa
 * lógica.
 */
class BleEventProxy(
    private val eventApi: EventoProxyRemoto,
    private val bleClient: BleClient,
) {
    suspend fun sincronizar(dispositivoId: String): List<EventoResponse> {
        val pendentes = bleClient.lerEventosPendentes(dispositivoId)
        if (pendentes.isEmpty()) return emptyList()

        val confirmados = eventApi.enviarProxyBle(dispositivoId, pendentes)
        bleClient.confirmarEventosEntregues(dispositivoId, pendentes.map { it.eventoId })
        return confirmados
    }
}
