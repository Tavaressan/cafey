package br.com.tavaressan.cafey.shared.domain.model

import kotlinx.serialization.Serializable

/** Ação de comando imediato — mesmo vocabulário do tópico MQTT `.../comando`
 * (`ComandoPayload.acao` em `br.com.tavaressan.cafey.mqtt.MqttPayloads`, spec §6.2, FW-30). */
enum class AcaoComando {
    LIGAR,
    DESLIGAR,
    CANCELAR,
    ESQUECER_REDE,
    ESQUECER_TODAS_REDES,
}

/**
 * Corpo de `POST /dispositivos/{id}/comando` (BE-27/FW-30). `duracaoS` só é considerado em
 * [AcaoComando.LIGAR]; `ssid` identifica a rede em [AcaoComando.ESQUECER_REDE] (nunca senha).
 */
@Serializable
data class ComandoRequest(
    val acao: AcaoComando,
    val duracaoS: Int? = null,
    val ssid: String? = null,
)

/**
 * Resposta de `POST /dispositivos/{id}/comando`, devolvida com 202: o comando foi publicado no
 * tópico MQTT, não executado. O estado do dispositivo só muda quando a base reporta de volta —
 * por isso aqui não vem [DispositivoResponse].
 *
 * `comandoId` é gerado pelo backend para o firmware deduplicar entregas repetidas do QoS 1.
 */
@Serializable
data class ComandoResponse(
    val comandoId: String,
    val acao: AcaoComando,
    val duracaoS: Int,
    val emitidoEm: String,
)
