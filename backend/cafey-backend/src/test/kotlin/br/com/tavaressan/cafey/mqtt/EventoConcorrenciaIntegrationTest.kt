package br.com.tavaressan.cafey.mqtt

import br.com.tavaressan.cafey.config.AwsIotProperties
import br.com.tavaressan.cafey.device.Dispositivo
import br.com.tavaressan.cafey.device.DispositivoRepository
import br.com.tavaressan.cafey.device.PapelDispositivo
import br.com.tavaressan.cafey.device.UsuarioDispositivo
import br.com.tavaressan.cafey.device.UsuarioDispositivoRepository
import br.com.tavaressan.cafey.event.EventoPreparoRepository
import br.com.tavaressan.cafey.event.EventoService
import br.com.tavaressan.cafey.user.Usuario
import br.com.tavaressan.cafey.user.UsuarioRepository
import tools.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lead Issue #286: [TEST/BE] Suíte de testes de concorrência e deduplicação na ingestão de eventos MQTT.
 *
 * Valida a integridade da deduplicação (ON CONFLICT (dispositivo_id, evento_id) DO NOTHING) e garantia
 * de incremento único de cuidados sob disparos simultâneos de eventos idênticos.
 */
@SpringBootTest
class EventoConcorrenciaIntegrationTest @Autowired constructor(
    private val mqttIngestionService: MqttIngestionService,
    private val eventoService: EventoService,
    private val dispositivoRepository: DispositivoRepository,
    private val eventoPreparoRepository: EventoPreparoRepository,
    private val usuarioRepository: UsuarioRepository,
    private val usuarioDispositivoRepository: UsuarioDispositivoRepository,
    private val jdbcTemplate: JdbcTemplate
) {

    private lateinit var dispositivo: Dispositivo
    private lateinit var proprietario: Usuario
    private lateinit var mqttClientService: MqttClientService

    @BeforeEach
    fun setUp() {
        proprietario = usuarioRepository.save(
            Usuario(nome = "Dono Teste Concorrencia", email = "dono-${UUID.randomUUID()}@cafey.com", senhaHash = "hash")
        )
        dispositivo = dispositivoRepository.save(
            Dispositivo(nome = "Cafeteira Concorrencia", limiarDescalcificacao = 200, contadorPreparos = 0)
        )
        usuarioDispositivoRepository.save(
            UsuarioDispositivo(usuario = proprietario, dispositivo = dispositivo, papel = PapelDispositivo.PROPRIETARIO)
        )

        val properties = AwsIotProperties()
        mqttClientService = MqttClientService(properties, mqttIngestionService, jacksonObjectMapper(), null)
    }

    @AfterEach
    fun tearDown() {
        jdbcTemplate.update("DELETE FROM eventos_preparo WHERE dispositivo_id = ?", dispositivo.id)
        usuarioDispositivoRepository.findByDispositivoId(dispositivo.id!!).forEach { usuarioDispositivoRepository.delete(it) }
        dispositivoRepository.delete(dispositivo)
        usuarioRepository.delete(proprietario)
    }

    @Test
    fun `disparos simultaneos de evento identico em multiplas threads deve persistir apenas um registro e incrementar descalcificacao uma unica vez`() {
        val dispId = dispositivo.id!!
        val eventoIdDuplicado = "evt-concorrente-${UUID.randomUUID()}"
        val threadCount = 20
        val startGate = CountDownLatch(1)
        val endGate = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)

        val payload = EventoPayload(
            eventoId = eventoIdDuplicado,
            tipo = "PREPARO",
            resultado = "CONCLUIDO",
            origem = "APP",
            duracaoS = 240,
            timestamp = Instant.now()
        )

        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    startGate.await()
                    mqttIngestionService.processarEvento(dispId, payload)
                } finally {
                    endGate.countDown()
                }
            }
        }

        // Libera todas as threads simultaneamente
        startGate.countDown()
        val finished = endGate.await(15, TimeUnit.SECONDS)
        executor.shutdown()
        assertEquals(true, finished, "Todas as threads concorrentes devem concluir dentro do tempo limite")

        // Validação da persistência: exatamente 1 registro persistido em eventos_preparo
        val eventosNoBanco = jdbcTemplate.queryForList(
            "SELECT evento_id, resultado FROM eventos_preparo WHERE dispositivo_id = ?",
            dispId
        )
        assertEquals(1, eventosNoBanco.size, "Apenas um registro de preparo deve ser persistido em eventos_preparo")
        assertEquals(eventoIdDuplicado, eventosNoBanco[0]["evento_id"])

        // Validação do contador de descalcificação: incrementado exatamente 1 vez
        val dispAtualizado = dispositivoRepository.findById(dispId).orElseThrow()
        assertEquals(1, dispAtualizado.contadorPreparos, "Contador de descalcificação deve ser incrementado apenas 1 vez")

        // Validação dos cuidados obtidos pelo serviço
        val cuidados = eventoService.obterCuidados(dispId, proprietario.id!!)
        assertEquals(1, cuidados.descalcificacao.contadorPreparos)
        assertEquals(1, cuidados.enxague.contadorPreparos)
        assertEquals(1, cuidados.filtro.contadorPreparos)
    }

    @Test
    fun `teste de carga concorrente disparando ingestao em lote com evento_id duplicado em multiplas threads`() {
        val dispId = dispositivo.id!!
        val eventoIdDuplicado = "evt-lote-duplicado-${UUID.randomUUID()}"
        val threadCount = 25
        val startGate = CountDownLatch(1)
        val endGate = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)

        // Simula disparo em lote onde cada thread submete o mesmo lote contendo o eventoId duplicado
        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    startGate.await()
                    val payload = EventoPayload(
                        eventoId = eventoIdDuplicado,
                        tipo = "PREPARO",
                        resultado = "CONCLUIDO",
                        origem = "ESP32",
                        duracaoS = 300,
                        timestamp = Instant.now()
                    )
                    mqttIngestionService.processarEvento(dispId, payload)
                } finally {
                    endGate.countDown()
                }
            }
        }

        startGate.countDown()
        val finished = endGate.await(15, TimeUnit.SECONDS)
        executor.shutdown()
        assertEquals(true, finished, "Carga concorrente em lote deve finalizar no tempo limite")

        val totalPersistido = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM eventos_preparo WHERE dispositivo_id = ? AND evento_id = ?",
            Long::class.java,
            dispId,
            eventoIdDuplicado
        )
        assertEquals(1L, totalPersistido, "Deve persistir exatamente um evento mesmo sob carga concorrente em lote")

        val dispAtualizado = dispositivoRepository.findById(dispId).orElseThrow()
        assertEquals(1, dispAtualizado.contadorPreparos, "Contador de descalcificação deve ser incrementado apenas 1 vez sob carga concorrente")
    }

    @Test
    fun `disparo concorrente de evento duplicado via topico MQTT deve deduplicar e incrementar cuidados uma unica vez`() {
        val dispId = dispositivo.id!!
        val eventoIdDuplicado = "evt-mqtt-concorrente-${UUID.randomUUID()}"
        val threadCount = 15
        val startGate = CountDownLatch(1)
        val endGate = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)

        val json = """
            {
                "eventoId": "$eventoIdDuplicado",
                "tipo": "PREPARO",
                "resultado": "CONCLUIDO",
                "origem": "BOTAO",
                "duracaoS": 180,
                "timestamp": "2026-10-23T18:00:00Z"
            }
        """.trimIndent()

        val topic = "dispositivos/$dispId/eventos"

        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    startGate.await()
                    mqttClientService.handleIncomingMessage(topic, json)
                } finally {
                    endGate.countDown()
                }
            }
        }

        startGate.countDown()
        val finished = endGate.await(15, TimeUnit.SECONDS)
        executor.shutdown()
        assertEquals(true, finished, "Disparo MQTT concorrente deve finalizar")

        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM eventos_preparo WHERE dispositivo_id = ? AND evento_id = ?",
            Long::class.java,
            dispId,
            eventoIdDuplicado
        )
        assertEquals(1L, count, "Apenas 1 registro deve ser persistido via tópico MQTT")

        val dispAtualizado = dispositivoRepository.findById(dispId).orElseThrow()
        assertEquals(1, dispAtualizado.contadorPreparos, "Contador de descalcificação deve ser incrementado 1 vez")
    }

    @Test
    fun `disparo simultaneo de evento com resultado FALHA persiste um registro mas nao incrementa descalcificacao`() {
        val dispId = dispositivo.id!!
        val eventoIdFalha = "evt-falha-${UUID.randomUUID()}"
        val threadCount = 10
        val startGate = CountDownLatch(1)
        val endGate = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)

        val payload = EventoPayload(
            eventoId = eventoIdFalha,
            tipo = "PREPARO",
            resultado = "FALHA",
            origem = "APP",
            duracaoS = 45,
            timestamp = Instant.now(),
            detalheErro = "Falta de agua"
        )

        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    startGate.await()
                    mqttIngestionService.processarEvento(dispId, payload)
                } finally {
                    endGate.countDown()
                }
            }
        }

        startGate.countDown()
        val finished = endGate.await(15, TimeUnit.SECONDS)
        executor.shutdown()
        assertEquals(true, finished)

        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM eventos_preparo WHERE dispositivo_id = ? AND evento_id = ?",
            Long::class.java,
            dispId,
            eventoIdFalha
        )
        assertEquals(1L, count, "Evento com falha deduplicado deve persistir 1 registro")

        val dispAtualizado = dispositivoRepository.findById(dispId).orElseThrow()
        assertEquals(0, dispAtualizado.contadorPreparos, "Preparo não CONCLUIDO não deve incrementar contador de descalcificação")
    }

    @Test
    fun `deduplicacao subsequente apos ingestao confirmada ignora reenvios e mantem contador intacto`() {
        val dispId = dispositivo.id!!
        val eventoId = "evt-subsequente-${UUID.randomUUID()}"
        val payload = EventoPayload(
            eventoId = eventoId,
            tipo = "PREPARO",
            resultado = "CONCLUIDO",
            origem = "APP",
            duracaoS = 200,
            timestamp = Instant.now()
        )

        // Ingestão inicial confirmada
        mqttIngestionService.processarEvento(dispId, payload)

        var dispAtualizado = dispositivoRepository.findById(dispId).orElseThrow()
        assertEquals(1, dispAtualizado.contadorPreparos)

        // Reenvios concorrentes subsequentes (ex: reconexão MQTT QoS 1)
        val threadCount = 10
        val startGate = CountDownLatch(1)
        val endGate = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)

        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    startGate.await()
                    mqttIngestionService.processarEvento(dispId, payload)
                } finally {
                    endGate.countDown()
                }
            }
        }

        startGate.countDown()
        endGate.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM eventos_preparo WHERE dispositivo_id = ? AND evento_id = ?",
            Long::class.java,
            dispId,
            eventoId
        )
        assertEquals(1L, count)

        dispAtualizado = dispositivoRepository.findById(dispId).orElseThrow()
        assertEquals(1, dispAtualizado.contadorPreparos, "Contador deve continuar em 1 após reenvios ignorados")
    }
}
