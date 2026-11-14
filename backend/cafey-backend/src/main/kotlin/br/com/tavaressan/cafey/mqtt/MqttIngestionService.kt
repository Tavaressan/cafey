package br.com.tavaressan.cafey.mqtt

import br.com.tavaressan.cafey.device.DispositivoRepository
import br.com.tavaressan.cafey.event.EventoService
import br.com.tavaressan.cafey.event.IngestaoEventoRequest
import br.com.tavaressan.cafey.notification.DispositivoOfflineEvent
import br.com.tavaressan.cafey.notification.PreparoIniciadoEvent
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
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

    companion object {
        /** Mesma duração da janela aberta pelo toque longo no firmware (kBindWindowS). */
        const val VINCULO_TTL_SEGUNDOS = 120L
    }

    @Transactional
    fun processarEstado(dispositivoId: UUID, payload: EstadoPayload) {
        val dispositivo = dispositivoRepository.findById(dispositivoId).orElse(null)
        if (dispositivo == null) {
            logger.warn("Recebido estado para dispositivo inexistente: {}", dispositivoId)
            return
        }

        val agora = Instant.now()
        val estavaPreparando = isBrewingState(dispositivo.estado)
        dispositivo.estado = payload.estado
        dispositivo.ultimoVisto = payload.desde ?: agora
        dispositivo.preparoIniciadoEm = if (isBrewingState(payload.estado)) {
            payload.desde ?: dispositivo.preparoIniciadoEm ?: agora
        } else {
            null
        }
        payload.firmware?.let { dispositivo.firmware = it }
        dispositivo.atualizadoEm = agora
        dispositivoRepository.save(dispositivo)
        logger.info("Estado do dispositivo {} atualizado para {}", dispositivoId, payload.estado)

        // Só a transição avisa: o estado retido é republicado a cada reconexão da base (#361).
        if (!estavaPreparando && isBrewingState(payload.estado)) {
            eventPublisher.publishEvent(PreparoIniciadoEvent(dispositivoId))
        }
    }

    private fun isBrewingState(estado: String): Boolean =
        estado.trim().uppercase() in setOf("PREPARANDO", "LIGADO", "BREWING")

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
        payload.rssi?.let { dispositivo.rssi = it }
        payload.uptimeS?.let { dispositivo.uptimeS = it }
        payload.ultimoReset?.let { dispositivo.ultimoReset = it.take(50) } // coluna VARCHAR(50); vem da placa
        payload.resetsAnormais?.let { dispositivo.resetsAnormais = it }
        dispositivo.atualizadoEm = Instant.now()
        dispositivoRepository.save(dispositivo)
        logger.info("Saúde do dispositivo {} atualizada: online={}", dispositivoId, payload.online)

        if (wasOffline && payload.online) {
            eventPublisher.publishEvent(DispositivoOnlineEvent(dispositivoId))
        }
        if (!wasOffline && !payload.online) {
            eventPublisher.publishEvent(DispositivoOfflineEvent(dispositivoId, dispositivo.ultimoVisto!!))
        }
    }

    /**
     * Guarda ou invalida o código de uso único da janela de vínculo da placa (FW-24, #275; FW-29).
     *
     * Abertura (`{"codigo":"123456"}`): o prazo é calculado no recebimento (e não pelo relógio da
     * placa, que pode não ter NTP), usando a mesma duração da janela do firmware. Uma nova
     * publicação sobrescreve o código anterior, o que cobre reabrir a janela antes de expirar.
     *
     * Fechamento/invalidação (`{"aberta":false,"codigo":null}`), manual ou por tempo: limpa o
     * código e a expiração para que o backend recuse o código antigo mesmo antes do prazo.
     */
    @Transactional
    fun processarVinculo(dispositivoId: UUID, payload: VinculoPayload) {
        val dispositivo = dispositivoRepository.findById(dispositivoId).orElse(null)
        if (dispositivo == null) {
            logger.warn("Recebida mensagem de vínculo para dispositivo inexistente: {}", dispositivoId)
            return
        }

        val agora = Instant.now()
        if (payload.aberta == false || payload.codigo == null) {
            dispositivo.codigoVinculo = null
            dispositivo.codigoVinculoExpiraEm = null
            dispositivo.atualizadoEm = agora
            dispositivoRepository.save(dispositivo)
            logger.info("Código de vínculo do dispositivo {} invalidado (janela fechada)", dispositivoId)
            return
        }

        val codigo = payload.codigo.trim()
        if (codigo.isEmpty()) {
            logger.warn("Recebido código de vínculo vazio para dispositivo {}", dispositivoId)
            return
        }

        dispositivo.codigoVinculo = codigo
        dispositivo.codigoVinculoExpiraEm = agora.plusSeconds(VINCULO_TTL_SEGUNDOS)
        dispositivo.atualizadoEm = agora
        dispositivoRepository.save(dispositivo)
        logger.info("Código de vínculo do dispositivo {} armazenado (expira em {}s)", dispositivoId, VINCULO_TTL_SEGUNDOS)
    }

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
        try {
            eventoService.ingestar(dispositivoId, req, notificar = true)
        } catch (e: DataIntegrityViolationException) {
            logger.info("Evento concorrente duplicado ignorado: dispositivo={}, eventoId={}", dispositivoId, payload.eventoId)
        }
    }
}
