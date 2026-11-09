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
    val rssi: Int? = null
)

data class ComandoPayload(
    val comandoId: String,
    val acao: String,
    val duracaoS: Int,
    val emitidoEm: Instant = Instant.now()
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
 * Janela de vínculo aberta/fechada pelo toque longo na placa (FW-24, #275; FW-29 fecha).
 *
 * Abertura (retrocompatível com o firmware do PR #299): `{"codigo":"123456"}` (sem retain).
 * Fechamento/invalidação (manual ou por tempo): `{"aberta":false,"codigo":null}` — sinaliza ao
 * backend que descarte o código para que ele não seja aceito depois da janela encerrar.
 */
data class VinculoPayload(
    val codigo: String? = null,
    val aberta: Boolean? = null
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
