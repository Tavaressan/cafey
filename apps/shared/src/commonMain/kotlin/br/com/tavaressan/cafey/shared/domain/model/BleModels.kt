package br.com.tavaressan.cafey.shared.domain.model

import kotlinx.serialization.Serializable

/**
 * Evento de preparo lido da fila local do ESP32 via BLE (spec §6.5, proxy BLE → nuvem). Mesmo
 * vocabulário de `IngestaoEventoRequest` no backend — o carimbo de tempo é gravado na origem
 * (no firmware) para o backend deduplicar entregas atrasadas por dispositivo + timestamp.
 */
@Serializable
data class BleEvento(
    val eventoId: String,
    val tipo: String = "PREPARO",
    val resultado: String,
    val origem: String,
    val duracaoS: Int = 0,
    val timestamp: String,
    val detalheErro: String? = null,
)

/** Corpo de `POST /dispositivos/{id}/eventos/proxy-ble` — espelha `ProxyBleEventosRequest`. */
@Serializable
data class ProxyBleEventosRequest(
    val eventos: List<BleEvento>,
)
