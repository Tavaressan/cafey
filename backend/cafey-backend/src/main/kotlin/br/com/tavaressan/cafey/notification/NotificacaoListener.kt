package br.com.tavaressan.cafey.notification

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.TaskExecutor
import org.springframework.core.task.TaskRejectedException
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.Duration
import java.time.Instant

/**
 * Liga a ingestão às notificações (#361): só depois do commit (nada é avisado se a gravação falhar) e
 * fora da thread do MQTT (um serviço de push lento não atrasa a ingestão). Fila cheia descarta a
 * notificação com log, em vez de bloquear.
 */
@Component
class NotificacaoListener(
    private val notificacaoService: NotificacaoService,
    @Qualifier(NotificacaoConfig.EXECUTOR) private val executor: TaskExecutor,
    private val scheduler: TaskScheduler,
    private val properties: NotificacaoProperties
) {
    private val logger = LoggerFactory.getLogger(NotificacaoListener::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun onEventoPreparo(evento: EventoPreparoRegistradoEvent) =
        despachar("evento ${evento.eventoId}") { notificacaoService.notificarEventoPreparo(evento) }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun onPreparoIniciado(evento: PreparoIniciadoEvent) =
        despachar("preparo iniciado em ${evento.dispositivoId}") {
            notificacaoService.notificarPreparoIniciado(evento.dispositivoId)
        }

    /** O aviso de offline espera o debounce: quedas curtas de Wi-Fi disparam o Last Will e voltam logo. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun onDispositivoOffline(evento: DispositivoOfflineEvent) {
        val quando = Instant.now().plus(Duration.ofSeconds(properties.offlineDebounceSegundos))
        scheduler.schedule({
            despachar("offline de ${evento.dispositivoId}") {
                notificacaoService.verificarOffline(evento.dispositivoId, evento.ultimoVisto)
            }
        }, quando)
    }

    private fun despachar(descricao: String, tarefa: () -> Unit) {
        try {
            executor.execute {
                runCatching(tarefa).onFailure { logger.error("Falha ao notificar {}", descricao, it) }
            }
        } catch (e: TaskRejectedException) {
            logger.warn("Fila de notificações cheia; {} descartado", descricao)
        }
    }
}
