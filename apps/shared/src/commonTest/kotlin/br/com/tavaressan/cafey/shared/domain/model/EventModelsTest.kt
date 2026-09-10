package br.com.tavaressan.cafey.shared.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

/** UC-14 — rótulos legíveis de `resultado`/`origem` para a lista de histórico (APP-06). */
class EventModelsTest {
    private fun evento(resultado: String, origem: String) = EventoResponse(
        id = "1",
        eventoId = "evt-1",
        tipo = "PREPARO",
        resultado = resultado,
        origem = origem,
        duracaoS = 120,
        timestamp = "2026-09-09T10:00:00Z",
        criadoEm = "2026-09-09T10:00:00Z",
    )

    @Test
    fun resultadoLabel_translatesKnownValues() {
        assertEquals("Concluído", evento("CONCLUIDO", "APP").resultadoLabel())
        assertEquals("Cancelado", evento("CANCELADO", "APP").resultadoLabel())
        assertEquals("Erro", evento("ERRO", "APP").resultadoLabel())
    }

    @Test
    fun resultadoLabel_fallsBackToRawValue() {
        assertEquals("PENDENTE", evento("PENDENTE", "APP").resultadoLabel())
    }

    @Test
    fun origemLabel_translatesKnownValues() {
        assertEquals("App", evento("CONCLUIDO", "APP").origemLabel())
        assertEquals("Agendamento", evento("CONCLUIDO", "AGENDAMENTO").origemLabel())
        assertEquals("Botão", evento("CONCLUIDO", "BOTAO").origemLabel())
    }
}
