package br.com.tavaressan.cafey.shared.ui.schedule

import br.com.tavaressan.cafey.shared.domain.model.AgendamentoResponse
import br.com.tavaressan.cafey.shared.domain.model.DURACOES_PREPARO_S
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Issue #189 — duração de preparo por agendamento no formulário. Issue #263 — campo descricao. */
class ScheduleFormStateTest {

    private fun agendamento(duracaoPreparoS: Int?, descricao: String? = null) = AgendamentoResponse(
        id = "ag-1",
        dispositivoId = "disp-1",
        hora = "07:00",
        diasSemana = 62,
        ativo = true,
        duracaoPreparoS = duracaoPreparoS,
        descricao = descricao,
        criadoEm = "2026-09-09T10:00:00Z",
        atualizadoEm = "2026-09-09T10:00:00Z",
    )

    @Test
    fun newForm_preselects8Minutes_likeThePrototype() {
        assertEquals(480, ScheduleFormState().duracaoPreparoS)
        assertEquals("", ScheduleFormState().descricao)
    }

    @Test
    fun editForm_carriesStoredDurationAndDescription() {
        val form = ScheduleFormState.forEdit(agendamento(360, "Café da manhã"))
        assertEquals(360, form.duracaoPreparoS)
        assertEquals("Café da manhã", form.descricao)
    }

    @Test
    fun editForm_keepsNullWhenAgendamentoUsesDeviceDuration() {
        // Agendamento sem duração própria cai no padrão do dispositivo (300 s por padrão, fora das
        // opções 4/6/8/10): pré-selecionar 8 min aqui trocaria a duração só por editar a hora.
        assertNull(ScheduleFormState.forEdit(agendamento(null)).duracaoPreparoS)
    }

    @Test
    fun cleared_emptiesFieldsAndKeepsTheAgendamentoBeingEdited() {
        val form = ScheduleFormState.forEdit(agendamento(360, "Café forte")).cleared()

        assertEquals("ag-1", form.agendamentoId)
        assertEquals("", form.hora)
        assertEquals(List(7) { false }, form.diasAtivos)
        assertNull(form.duracaoPreparoS)
        assertEquals("", form.descricao)
    }

    @Test
    fun options_matchTheBackendAllowedValues() {
        assertEquals(listOf(240, 360, 480, 600), DURACOES_PREPARO_S)
    }
}
