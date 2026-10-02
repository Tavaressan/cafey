package br.com.tavaressan.cafey.schedule

import jakarta.validation.Validation
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.api.Test

/** Bean Validation roda no controller (`@Valid`), então o teste valida os DTOs diretamente. */
class ScheduleDtoValidationTest {

    companion object {
        private val factory = Validation.buildDefaultValidatorFactory()
        private val validator = factory.validator

        @JvmStatic
        @AfterAll
        fun closeFactory() = factory.close()
    }

    private fun criar(duracao: Int?) = CriarAgendamentoRequest(hora = "07:00", diasSemana = 62, duracaoPreparoS = duracao)

    @ParameterizedTest
    @ValueSource(ints = [240, 360, 480, 600])
    fun `should accept allowed duracaoPreparoS`(duracao: Int) {
        assertTrue(validator.validate(criar(duracao)).isEmpty())
        assertTrue(validator.validate(AtualizarAgendamentoRequest(duracaoPreparoS = duracao)).isEmpty())
    }

    @Test
    fun `should accept null duracaoPreparoS`() {
        assertTrue(validator.validate(criar(null)).isEmpty())
        assertTrue(validator.validate(AtualizarAgendamentoRequest(duracaoPreparoS = null)).isEmpty())
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 30, 300, 241, 900, -240])
    fun `should reject duracaoPreparoS outside the allowed set`(duracao: Int) {
        val criarViolations = validator.validate(criar(duracao))
        assertEquals(listOf("duracaoPreparoS"), criarViolations.map { it.propertyPath.toString() })

        val atualizarViolations = validator.validate(AtualizarAgendamentoRequest(duracaoPreparoS = duracao))
        assertEquals(listOf("duracaoPreparoS"), atualizarViolations.map { it.propertyPath.toString() })
    }
}
