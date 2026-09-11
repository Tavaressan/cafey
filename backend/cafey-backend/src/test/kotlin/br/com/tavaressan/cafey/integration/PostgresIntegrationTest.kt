package br.com.tavaressan.cafey.integration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

/**
 * Suíte de integração que roda as migrações Flyway reais (V1..V6) contra um
 * PostgreSQL real via Testcontainers, validando recursos específicos do
 * banco (citext, tipos de data, índices únicos) que o H2 usado nos testes
 * unitários não reproduz fielmente.
 *
 * Ver issue #102.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("integration")
class PostgresIntegrationTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:16-alpine")
    }

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `flyway aplica as migracoes V1 a V6 no schema_version`() {
        val versions = jdbcTemplate.queryForList(
            "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank",
            String::class.java,
        )

        assertEquals(listOf("1", "2", "3", "4", "5", "6"), versions)
    }

    @Test
    @Transactional
    fun `citext torna o email de usuarios case-insensitive e unico`() {
        insertUsuario(email = "Pessoa@Exemplo.com")

        assertThrows(DataIntegrityViolationException::class.java) {
            insertUsuario(email = "pessoa@exemplo.com")
        }
    }

    @Test
    fun `agendamentos usa colunas time e timestamptz`() {
        val columnTypes = jdbcTemplate.queryForList(
            """
            SELECT column_name, data_type FROM information_schema.columns
            WHERE table_name = 'agendamentos'
              AND column_name IN ('hora', 'criado_em', 'atualizado_em')
            """.trimIndent(),
        ).associate { it["column_name"] to it["data_type"] }

        assertEquals("time without time zone", columnTypes["hora"])
        assertEquals("timestamp with time zone", columnTypes["criado_em"])
        assertEquals("timestamp with time zone", columnTypes["atualizado_em"])
    }

    @Test
    fun `refresh_tokens possui indice unico em token_hash`() {
        val hasUniqueIndex = jdbcTemplate.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1 FROM pg_indexes
                WHERE tablename = 'refresh_tokens' AND indexdef ILIKE '%UNIQUE%token_hash%'
            )
            """.trimIndent(),
            Boolean::class.java,
        )

        assertTrue(hasUniqueIndex ?: false)
    }

    private fun insertUsuario(email: String) {
        jdbcTemplate.update(
            "INSERT INTO usuarios (id, nome, email, senha_hash) VALUES (?, ?, ?, ?)",
            UUID.randomUUID(),
            "Pessoa Teste",
            email,
            "hash",
        )
    }
}
