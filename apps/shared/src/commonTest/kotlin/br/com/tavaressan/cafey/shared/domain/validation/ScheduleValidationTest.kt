package br.com.tavaressan.cafey.shared.domain.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScheduleValidationTest {

    @Test
    fun schedule_validForm_hasNoErrors() {
        val errors = ScheduleFormErrors.validate(hora = "06:45", diasSemana = 62 /* seg-sex */)
        assertTrue(errors.isValid)
    }

    @Test
    fun schedule_blankHora_isRequired() {
        val errors = ScheduleFormErrors.validate(hora = "", diasSemana = 62)
        assertEquals(FieldError.Required, errors.hora)
    }

    @Test
    fun schedule_malformedHora_isInvalid() {
        // Backend exige o mesmo regex em CriarAgendamentoRequest.hora.
        val errors = ScheduleFormErrors.validate(hora = "25:00", diasSemana = 62)
        assertEquals(FieldError.InvalidTime, errors.hora)
    }

    @Test
    fun schedule_noDaySelected_isRejected() {
        // Backend exige diasSemana entre 1 e 127 (CriarAgendamentoRequest.diasSemana).
        val errors = ScheduleFormErrors.validate(hora = "06:45", diasSemana = 0)
        assertEquals(FieldError.NoDaySelected, errors.diasSemana)
    }
}
