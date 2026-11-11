package br.com.tavaressan.cafey.mqtt

import java.time.Instant

data class EstadoPayload(
    val estado: String,
    val desde: Instant? = null,
    val firmware: String? = null
)

data class SaudePayload(
    val online: Boolean,
    val uptimeS: Long? = null,
    val rssi: Int? = null,
    val ultimoReset: String? = null,
    val resetsAnormais: Int? = null
)

data class ComandoPayload(
    val comandoId: String,
    val acao: String,
    val duracaoS: Int,
    val emitidoEm: Instant = Instant.now(),
    val ssid: String? = null
)

data class AgendamentoItemPayload(
    val id: String,
    val hora: String,
    val diasSemana: Int,
    val ativo: Boolean,
    // Já resolvido: duração do agendamento ou, na falta, a do dispositivo.
    val duracaoS: Int
)

data class AgendamentosPayload(
    val versao: Int,
    val timezone: String,
    val duracaoS: Int,
    val agendamentos: List<AgendamentoItemPayload>
)

/**
 * Código de uso único da janela de vínculo aberta pelo toque longo na placa (FW-24, #275).
 * O firmware publica sem retain e só enquanto a janela está aberta.
 */
data class VinculoPayload(
    val codigo: String? = null
)

data class EventoPayload(
    val eventoId: String,
    val tipo: String = "PREPARO",
    val resultado: String,
    val origem: String,
    val duracaoS: Int,
    val timestamp: Instant,
    val detalheErro: String? = null
)
