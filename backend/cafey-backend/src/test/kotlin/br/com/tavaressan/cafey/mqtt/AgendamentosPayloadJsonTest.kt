package br.com.tavaressan.cafey.mqtt

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper

/**
 * O `mini_json` do firmware devolve a primeira ocorrência de uma chave: o `duracaoS` do topo
 * precisa vir antes dos `duracaoS` por item, senão o firmware leria o de um agendamento.
 */
class AgendamentosPayloadJsonTest {

    @Test
    fun `should serialize top level duracaoS before per-item duracaoS`() {
        val payload = AgendamentosPayload(
            versao = 1,
            timezone = "America/Sao_Paulo",
            duracaoS = 300,
            agendamentos = listOf(AgendamentoItemPayload("id", "07:00", 62, true, 480))
        )

        val json = jacksonObjectMapper().writeValueAsString(payload)

        val primeiro = json.indexOf("\"duracaoS\"")
        assertTrue(primeiro in 0 until json.indexOf("\"agendamentos\""), json)
        assertTrue(json.contains("\"duracaoS\":480"), json)
    }
}
