package br.com.cafey.mqtt

import br.com.cafey.device.DispositivoRepository
import br.com.cafey.event.EventoService
import br.com.cafey.event.IngestaoEventoRequest
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class DispositivoOnlineEvent(val dispositivoId: UUID)

@Service
class MqttIngestionService(
    private val dispositivoRepository: DispositivoRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val eventoService: EventoService
) {
    private val logger = LoggerFactory.getLogger(MqttIngestionService::class.java)

    @Transactional
    fun processarEstado(dispositivoId: UUID, payload: EstadoPayload) {
        val dispositivo = dispositivoRepository.findById(dispositivoId).orElse(null)
        if (dispositivo == null) {
            logger.warn("Recebido estado para dispositivo inexistente: {}", dispositivoId)
            return
        }

        dispositivo.estado = payload.estado
        dispositivo.ultimoVisto = payload.desde ?: Instant.now()
        dispositivo.atualizadoEm = Instant.now()
        dispositivoRepository.save(dispositivo)
        logger.info("Estado do dispositivo {} atualizado para {}", dispositivoId, payload.estado)
    }

    @Transactional
    fun processarSaude(dispositivoId: UUID, payload: SaudePayload) {
        val dispositivo = dispositivoRepository.findById(dispositivoId).orElse(null)
        if (dispositivo == null) {
            logger.warn("Recebida mensagem de saúde para dispositivo inexistente: {}", dispositivoId)
            return
        }

        val wasOffline = !dispositivo.online
        dispositivo.online = payload.online
        dispositivo.ultimoVisto = Instant.now()
        dispositivo.atualizadoEm = Instant.now()
        dispositivoRepository.save(dispositivo)
        logger.info("Saúde do dispositivo {} atualizada: online={}", dispositivoId, payload.online)

        if (wasOffline && payload.online) {
            eventPublisher.publishEvent(DispositivoOnlineEvent(dispositivoId))
        }
    }

    @Transactional
    fun processarEvento(dispositivoId: UUID, payload: EventoPayload) {
        val req = IngestaoEventoRequest(
            eventoId = payload.eventoId,
            tipo = payload.tipo,
            resultado = payload.resultado,
            origem = payload.origem,
            duracaoS = payload.duracaoS,
            timestamp = payload.timestamp,
            detalheErro = payload.detalheErro
        )
        eventoService.ingestar(dispositivoId, req)
    }
}
