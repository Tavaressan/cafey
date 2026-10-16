package br.com.tavaressan.cafey.shared.domain.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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

    @Test
    fun device_blankOptionalNumbers_areValid() {
        // Vazio vira null no request: padrão do backend no cadastro, valor atual mantido na edição.
        val errors = DeviceFormErrors.validate(nome = "Cafeteira", duracaoPreparoS = " ", limiarDescalcificacao = "")
        assertTrue(errors.isValid)
    }

    @Test
    fun device_duracaoOutsideBackendRange_isRejected() {
        // Mesmos limites de @Min(30)/@Max(900) em CriarDispositivoRequest.duracaoPreparoS.
        val esperado = FieldError.OutOfRange(30, 900)
        assertEquals(esperado, DeviceFormErrors.validate(nome = "x", duracaoPreparoS = "29").duracaoPreparoS)
        assertEquals(esperado, DeviceFormErrors.validate(nome = "x", duracaoPreparoS = "901").duracaoPreparoS)
        assertEquals(esperado, DeviceFormErrors.validate(nome = "x", duracaoPreparoS = "abc").duracaoPreparoS)
        assertNull(DeviceFormErrors.validate(nome = "x", duracaoPreparoS = "900").duracaoPreparoS)
    }

    @Test
    fun device_limiarMustBePositive() {
        assertEquals(FieldError.NotPositive, DeviceFormErrors.validate(nome = "x", limiarDescalcificacao = "0").limiarDescalcificacao)
        assertEquals(FieldError.NotPositive, DeviceFormErrors.validate(nome = "x", limiarDescalcificacao = "1,5").limiarDescalcificacao)
        assertNull(DeviceFormErrors.validate(nome = "x", limiarDescalcificacao = "1").limiarDescalcificacao)
    }
}
