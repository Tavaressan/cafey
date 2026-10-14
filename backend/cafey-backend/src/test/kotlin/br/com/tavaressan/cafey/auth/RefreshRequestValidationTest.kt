package br.com.tavaressan.cafey.auth

import jakarta.validation.Validation
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/** Bean Validation roda no controller (`@Valid`), então o teste valida o DTO diretamente. */
class RefreshRequestValidationTest {

    companion object {
        private val factory = Validation.buildDefaultValidatorFactory()
        private val validator = factory.validator

        @JvmStatic
        @AfterAll
        fun closeFactory() = factory.close()
    }

    @Test
    fun `should accept a refresh token like the ones the server issues`() {
        // O JwtTokenService gera dois UUIDs textuais concatenados: 72 caracteres.
        val token = UUID.randomUUID().toString() + UUID.randomUUID().toString()
        assertEquals(72, token.length)
        assertTrue(validator.validate(RefreshRequest(token)).isEmpty())
    }

    @Test
    fun `should accept a refresh token with exactly 100 characters`() {
        assertTrue(validator.validate(RefreshRequest("a".repeat(100))).isEmpty())
    }

    @Test
    fun `should reject a refresh token with 101 characters`() {
        val violations = validator.validate(RefreshRequest("a".repeat(101)))
        assertEquals(listOf("refreshToken"), violations.map { it.propertyPath.toString() })
        assertEquals(listOf("RefreshToken inválido"), violations.map { it.message })
    }

    @Test
    fun `should reject a blank refresh token`() {
        val violations = validator.validate(RefreshRequest("   "))
        assertEquals(listOf("RefreshToken é obrigatório"), violations.map { it.message })
    }
}
