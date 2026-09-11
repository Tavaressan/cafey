package br.com.tavaressan.cafey.shared.domain.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthValidationTest {

    @Test
    fun login_validCredentials_hasNoErrors() {
        val errors = LoginFormErrors.validate(email = "sandro@example.com", senha = "segredo123")
        assertTrue(errors.isValid)
    }

    @Test
    fun login_invalidEmail_isRejected() {
        val errors = LoginFormErrors.validate(email = "nao-e-email", senha = "segredo123")
        assertEquals(FieldError.InvalidEmail, errors.email)
    }

    @Test
    fun login_blankPassword_isRequired() {
        val errors = LoginFormErrors.validate(email = "sandro@example.com", senha = "")
        assertEquals(FieldError.Required, errors.senha)
    }

    @Test
    fun register_shortPassword_isRejected() {
        // Backend exige @Size(min = 6) em RegisterRequest.senha.
        val errors = RegisterFormErrors.validate(nome = "Sandro", email = "sandro@example.com", senha = "abc")
        assertEquals(FieldError.PasswordTooShort, errors.senha)
        assertNull(errors.nome)
        assertNull(errors.email)
    }

    @Test
    fun register_blankNome_isRequired() {
        val errors = RegisterFormErrors.validate(nome = "  ", email = "sandro@example.com", senha = "segredo123")
        assertEquals(FieldError.Required, errors.nome)
    }
}
