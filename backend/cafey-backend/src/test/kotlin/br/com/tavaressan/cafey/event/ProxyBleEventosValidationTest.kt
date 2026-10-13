package br.com.tavaressan.cafey.event

import jakarta.validation.Validation
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/** Bean Validation roda no controller (`@Valid`), então o teste valida os DTOs diretamente. */
class ProxyBleEventosValidationTest {

    companion object {
        private val factory = Validation.buildDefaultValidatorFactory()
        private val validator = factory.validator

        @JvmStatic
        @AfterAll
        fun closeFactory() = factory.close()
    }

    private fun evento(
        eventoId: String = "evt-1",
        resultado: String = "CONCLUIDO",
        origem: String = "APP"
    ) = IngestaoEventoRequest(eventoId = eventoId, resultado = resultado, origem = origem, timestamp = Instant.now())

    private fun lote(quantidade: Int) = ProxyBleEventosRequest(List(quantidade) { evento(eventoId = "evt-$it") })

    @Test
    fun `should accept a batch with one valid event`() {
        assertTrue(validator.validate(lote(1)).isEmpty())
    }

    @Test
    fun `should accept a batch with exactly 100 events`() {
        assertTrue(validator.validate(lote(100)).isEmpty())
    }

    @Test
    fun `should reject an empty batch`() {
        val violations = validator.validate(lote(0))
        assertEquals(listOf("eventos"), violations.map { it.propertyPath.toString() })
        assertEquals(listOf("Lote de eventos não pode ser vazio"), violations.map { it.message })
    }

    @Test
    fun `should reject a batch with 101 events`() {
        val violations = validator.validate(lote(101))
        assertEquals(listOf("eventos"), violations.map { it.propertyPath.toString() })
        assertEquals(listOf("Lote de eventos deve ter no máximo 100 eventos"), violations.map { it.message })
    }

    @Test
    fun `should validate each element of the batch in cascade`() {
        val request = ProxyBleEventosRequest(
            listOf(
                evento(eventoId = "  "),
                evento(),
                evento(resultado = ""),
                evento(origem = " ")
            )
        )

        val campos = validator.validate(request).map { it.propertyPath.toString() }.toSet()

        assertEquals(setOf("eventos[0].eventoId", "eventos[2].resultado", "eventos[3].origem"), campos)
    }
}
