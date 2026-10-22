package br.com.tavaressan.cafey.integration

import br.com.tavaressan.cafey.event.EventoPreparoRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.core.io.ClassPathResource
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceUtils
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Suíte de integração que roda as migrações Flyway reais (V1..V9) contra um
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

    @Autowired
    private lateinit var eventoPreparoRepository: EventoPreparoRepository

    @Test
    fun `flyway aplica as migracoes V1 a V9 no schema_version`() {
        val versions = jdbcTemplate.queryForList(
            "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank",
            String::class.java,
        )

        assertEquals(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9"), versions)
    }

    @Test
    @Transactional
    fun `migration V9 corrige timezone legado nao suportado e preserva os suportados`() {
        val invalidoId = UUID.randomUUID()
        val validoId = UUID.randomUUID()
        jdbcTemplate.update(
            "INSERT INTO dispositivos (id, nome, timezone) VALUES (?, ?, ?), (?, ?, ?)",
            invalidoId,
            "Timezone legado inválido",
            "BRT",
            validoId,
            "Timezone válido",
            "America/Manaus",
        )

        val dataSource = requireNotNull(jdbcTemplate.dataSource)
        val connection = DataSourceUtils.getConnection(dataSource)
        try {
            ScriptUtils.executeSqlScript(
                connection,
                ClassPathResource("db/migration/V9__corrigir_timezone_dispositivo_nao_suportado.sql")
            )
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource)
        }

        val timezones = jdbcTemplate.queryForList(
            "SELECT timezone FROM dispositivos WHERE id IN (?, ?) ORDER BY id",
            String::class.java,
            invalidoId,
            validoId,
        )
        assertEquals(1, timezones.count { it == "America/Sao_Paulo" })
        assertEquals(1, timezones.count { it == "America/Manaus" })
    }

    @Test
    fun `agendamentos descricao e varchar 100 nullable`() {
        val column = jdbcTemplate.queryForMap(
            """
            SELECT data_type, character_maximum_length, is_nullable
            FROM information_schema.columns
            WHERE table_name = 'agendamentos' AND column_name = 'descricao'
            """.trimIndent(),
        )
        assertEquals("character varying", column["data_type"])
        assertEquals(100, column["character_maximum_length"])
        assertEquals("YES", column["is_nullable"])
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

    // Regressão: no PostgreSQL, os filtros opcionais nulos de findEventosComFiltro quebravam
    // GET /dispositivos/{id}/eventos com 500 (SQLState 42P18).
    @Test
    @Transactional
    fun `findEventosComFiltro aceita filtros nulos e filtra por resultado e periodo`() {
        val dispositivoId = UUID.randomUUID()
        jdbcTemplate.update("INSERT INTO dispositivos (id, nome) VALUES (?, ?)", dispositivoId, "Cafeteira")
        val t1 = Instant.parse("2026-10-01T10:00:00Z")
        val t2 = Instant.parse("2026-10-02T10:00:00Z")
        insertEvento(dispositivoId, "e1", "CONCLUIDO", t1)
        insertEvento(dispositivoId, "e2", "ERRO", t2)
        val pagina = PageRequest.of(0, 20)

        fun ids(resultado: String?, inicio: Instant?, fim: Instant?) =
            eventoPreparoRepository.findEventosComFiltro(dispositivoId, resultado, inicio, fim, pagina)
                .content.map { it.eventoId }

        assertEquals(listOf("e2", "e1"), ids(null, null, null))
        assertEquals(listOf("e1"), ids("CONCLUIDO", null, null))
        assertEquals(listOf("e2"), ids(null, t2, null))
        assertEquals(listOf("e1"), ids(null, null, t1))
        assertEquals(listOf("e1"), ids(null, t1, t1))
    }

    private fun insertEvento(dispositivoId: UUID, eventoId: String, resultado: String, timestamp: Instant) {
        jdbcTemplate.update(
            "INSERT INTO eventos_preparo (evento_id, dispositivo_id, resultado, origem, duracao_s, timestamp) " +
                "VALUES (?, ?, ?, 'APP', 60, ?)",
            eventoId,
            dispositivoId,
            resultado,
            Timestamp.from(timestamp),
        )
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
