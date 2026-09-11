package br.com.tavaressan.cafey.shared.domain.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceValidationTest {

    @Test
    fun device_validForm_hasNoErrors() {
        val errors = DeviceFormErrors.validate(nome = "Cafeteira da cozinha")
        assertTrue(errors.isValid)
    }

    @Test
    fun device_blankNome_isRequired() {
        // Backend exige o mesmo campo em CriarDispositivoRequest.nome (@NotBlank).
        val errors = DeviceFormErrors.validate(nome = "   ")
        assertEquals(FieldError.Required, errors.nome)
    }
}
