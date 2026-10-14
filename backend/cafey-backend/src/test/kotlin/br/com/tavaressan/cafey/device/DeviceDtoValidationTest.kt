package br.com.tavaressan.cafey.device

import jakarta.validation.ConstraintViolation
import jakarta.validation.Validation
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** Bean Validation roda no controller (`@Valid`), então o teste valida os DTOs diretamente. */
class DeviceDtoValidationTest {

    companion object {
        private val factory = Validation.buildDefaultValidatorFactory()
        private val validator = factory.validator

        @JvmStatic
        @AfterAll
        fun closeFactory() = factory.close()
    }

    private fun criar(
        nome: String = "Cafeteira Cozinha",
        timezone: String? = null,
        duracao: Int? = null,
        limiar: Int? = null
    ) = CriarDispositivoRequest(nome = nome, timezone = timezone, duracaoPreparoS = duracao, limiarDescalcificacao = limiar)

    private fun atualizar(
        nome: String? = null,
        timezone: String? = null,
        duracao: Int? = null,
        limiar: Int? = null
    ) = AtualizarDispositivoRequest(nome = nome, timezone = timezone, duracaoPreparoS = duracao, limiarDescalcificacao = limiar)

    private fun campos(violations: Set<ConstraintViolation<*>>) = violations.map { it.propertyPath.toString() }

    @Test
    fun `should accept requests with only the required fields`() {
        assertTrue(validator.validate(criar()).isEmpty())
        assertTrue(validator.validate(atualizar()).isEmpty())
    }

    @Test
    fun `should accept requests with every field inside the limits`() {
        assertTrue(validator.validate(criar(timezone = "America/Sao_Paulo", duracao = 300, limiar = 200)).isEmpty())
        assertTrue(
            validator.validate(atualizar(nome = "Nova", timezone = "America/Sao_Paulo", duracao = 300, limiar = 200)).isEmpty()
        )
    }

    // --- duracaoPreparoS ---

    @ParameterizedTest
    @ValueSource(ints = [30, 300, 900])
    fun `should accept duracaoPreparoS from 30 to 900`(duracao: Int) {
        assertTrue(validator.validate(criar(duracao = duracao)).isEmpty())
        assertTrue(validator.validate(atualizar(duracao = duracao)).isEmpty())
    }

    @ParameterizedTest
    @ValueSource(ints = [-30, 0, 10, 29, 901, 10000])
    fun `should reject duracaoPreparoS outside 30 to 900`(duracao: Int) {
        assertEquals(listOf("duracaoPreparoS"), campos(validator.validate(criar(duracao = duracao))))
        assertEquals(listOf("duracaoPreparoS"), campos(validator.validate(atualizar(duracao = duracao))))
    }

    @Test
    fun `should explain the duracaoPreparoS limits in portuguese`() {
        assertEquals(
            listOf("Duração de preparo deve ter no mínimo 30s"),
            validator.validate(atualizar(duracao = 10)).map { it.message }
        )
        assertEquals(
            listOf("Duração de preparo deve ter no máximo 900s"),
            validator.validate(atualizar(duracao = 901)).map { it.message }
        )
    }

    // --- limiarDescalcificacao ---

    @ParameterizedTest
    @ValueSource(ints = [1, 200, 100000])
    fun `should accept positive limiarDescalcificacao`(limiar: Int) {
        assertTrue(validator.validate(criar(limiar = limiar)).isEmpty())
        assertTrue(validator.validate(atualizar(limiar = limiar)).isEmpty())
    }

    @ParameterizedTest
    @ValueSource(ints = [-5, 0])
    fun `should reject limiarDescalcificacao lower than or equal to zero`(limiar: Int) {
        assertEquals(listOf("limiarDescalcificacao"), campos(validator.validate(criar(limiar = limiar))))
        assertEquals(listOf("limiarDescalcificacao"), campos(validator.validate(atualizar(limiar = limiar))))
        assertEquals(
            listOf("Limiar de descalcificação deve ser maior que zero"),
            validator.validate(atualizar(limiar = limiar)).map { it.message }
        )
    }

    // --- timezone ---

    @Test
    fun `should accept timezone with exactly 50 characters`() {
        val timezone = "a".repeat(50)
        assertTrue(validator.validate(criar(timezone = timezone)).isEmpty())
        assertTrue(validator.validate(atualizar(timezone = timezone)).isEmpty())
    }

    @Test
    fun `should reject timezone with 51 characters`() {
        val timezone = "a".repeat(51)
        assertEquals(listOf("timezone"), campos(validator.validate(criar(timezone = timezone))))
        assertEquals(listOf("timezone"), campos(validator.validate(atualizar(timezone = timezone))))
        assertEquals(
            listOf("Timezone deve ter no máximo 50 caracteres"),
            validator.validate(atualizar(timezone = timezone)).map { it.message }
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "   ", "\n", "\t "])
    fun `should reject blank timezone when it is informed`(timezone: String) {
        assertEquals(listOf("timezone"), campos(validator.validate(criar(timezone = timezone))))
        assertEquals(listOf("timezone"), campos(validator.validate(atualizar(timezone = timezone))))
        assertEquals(
            listOf("Timezone não pode ser vazio"),
            validator.validate(atualizar(timezone = timezone)).map { it.message }
        )
    }

    // --- nome ---

    @Test
    fun `should accept nome with exactly 255 characters`() {
        val nome = "a".repeat(255)
        assertTrue(validator.validate(criar(nome = nome)).isEmpty())
        assertTrue(validator.validate(atualizar(nome = nome)).isEmpty())
    }

    @Test
    fun `should reject nome with 256 characters`() {
        val nome = "a".repeat(256)
        assertEquals(listOf("nome"), campos(validator.validate(criar(nome = nome))))
        assertEquals(listOf("nome"), campos(validator.validate(atualizar(nome = nome))))
    }

    @Test
    fun `should accept nome with a line break`() {
        // O `.` do regex não casa quebra de linha sem o DOTALL; o PUT não pode recusar o que o POST aceita.
        assertTrue(validator.validate(criar(nome = "Cafeteira\nCozinha")).isEmpty())
        assertTrue(validator.validate(atualizar(nome = "Cafeteira\nCozinha")).isEmpty())
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "   ", "\n", "\t "])
    fun `should reject blank nome`(nome: String) {
        assertEquals(listOf("nome"), campos(validator.validate(criar(nome = nome))))
        assertEquals(listOf("nome"), campos(validator.validate(atualizar(nome = nome))))
    }

    @Test
    fun `should explain the nome rules in portuguese`() {
        assertEquals(
            listOf("Nome do dispositivo é obrigatório"),
            validator.validate(criar(nome = "  ")).map { it.message }
        )
        assertEquals(
            listOf("Nome do dispositivo não pode ser vazio"),
            validator.validate(atualizar(nome = "  ")).map { it.message }
        )
        assertEquals(
            listOf("Nome do dispositivo deve ter no máximo 255 caracteres"),
            validator.validate(atualizar(nome = "a".repeat(256))).map { it.message }
        )
    }

    @Test
    fun `should measure the nome size before trimming`() {
        // 255 caracteres úteis mais um espaço: o trim do mapper o reduziria a 255, mas a validação olha o valor bruto.
        val nome = "a".repeat(255) + " "
        assertEquals(listOf("nome"), campos(validator.validate(criar(nome = nome))))
        assertEquals(listOf("nome"), campos(validator.validate(atualizar(nome = nome))))
    }
}
