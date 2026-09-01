package br.com.cafey.mqtt

import java.time.Instant

data class EstadoPayload(
    val estado: String,
    val desde: Instant? = null,
    val firmware: String? = null
)

data class SaudePayload(
    val online: Boolean,
    val uptimeS: Long? = null,
    val rssi: Int? = null
)

data class ComandoPayload(
    val comandoId: String,
    val acao: String,
    val duracaoS: Int,
    val emitidoEm: Instant = Instant.now()
)
