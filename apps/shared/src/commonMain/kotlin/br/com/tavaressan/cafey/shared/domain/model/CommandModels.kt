package br.com.tavaressan.cafey.shared.domain.model

import kotlinx.serialization.Serializable

/** Ação de comando imediato — mesmo vocabulário do tópico MQTT `.../comando`
 * (`ComandoPayload.acao` em `br.com.tavaressan.cafey.mqtt.MqttPayloads`, spec §6.2). */
enum class AcaoComando {
    LIGAR,
    DESLIGAR,
    CANCELAR,
}

/**
 * **Contrato assumido, não confirmado no backend.** `EventoController`/`DispositivoController`
 * hoje só expõem leitura de estado (`GET /dispositivos/{id}`, que devolve o campo `estado`) — não
 * há, até este ponto do backlog, um endpoint REST que publique no tópico MQTT `.../comando`
 * descrito na spec §6.2 (só existe o payload `ComandoPayload` e o publish de agendamentos).
 * UC-06/07/09 (ligar, desligar, cancelar) dependem desse endpoint existir.
 *
 * Modelei o corpo espelhando `ComandoPayload` porque é o formato mais provável dado o que já
 * existe, mas o endpoint em si (`POST /dispositivos/{id}/comando`) precisa ser confirmado ou
 * criado no backend antes deste código funcionar contra o servidor real.
 */
@Serializable
data class ComandoRequest(
    val acao: AcaoComando,
    val duracaoS: Int? = null,
)
