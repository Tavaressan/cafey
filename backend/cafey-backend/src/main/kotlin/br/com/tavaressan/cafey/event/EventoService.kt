package br.com.cafey.event

import br.com.cafey.device.DispositivoRepository
import br.com.cafey.device.PapelDispositivo
import br.com.cafey.device.UsuarioDispositivoRepository
import br.com.cafey.exception.BadCredentialsException
import br.com.cafey.exception.ResourceNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class EventoService(
    private val eventoPreparoRepository: EventoPreparoRepository,
    private val dispositivoRepository: DispositivoRepository,
    private val usuarioDispositivoRepository: UsuarioDispositivoRepository
) {
    private val logger = LoggerFactory.getLogger(EventoService::class.java)

    @Transactional
    fun ingestar(dispositivoId: UUID, request: IngestaoEventoRequest): EventoResponse? {
        val dispositivo = dispositivoRepository.findById(dispositivoId).orElse(null) ?: return null

        if (eventoPreparoRepository.existsByDispositivoIdAndEventoId(dispositivoId, request.eventoId)) {
            logger.info("Evento duplicado ignorado: dispositivo={}, eventoId={}", dispositivoId, request.eventoId)
            return null
        }

        val evento = EventoPreparo(
            eventoId = request.eventoId,
            dispositivo = dispositivo,
            tipo = request.tipo,
            resultado = request.resultado.uppercase(),
            origem = request.origem.uppercase(),
            duracaoS = request.duracaoS,
            timestamp = request.timestamp,
            detalheErro = request.detalheErro
        )
        val salvo = eventoPreparoRepository.save(evento)

        // Incrementa contador de preparos somente em caso de sucesso (CONCLUIDO)
        if ("CONCLUIDO" == evento.resultado) {
            dispositivo.contadorPreparos += 1
            dispositivo.atualizadoEm = Instant.now()
            dispositivoRepository.save(dispositivo)
            logger.info("Contador de descalcificação do dispositivo {} incrementado para {}", dispositivoId, dispositivo.contadorPreparos)
        }

        return toResponse(salvo)
    }

    @Transactional(readOnly = true)
    fun listar(
        dispositivoId: UUID,
        usuarioId: UUID,
        resultado: String?,
        inicio: Instant?,
        fim: Instant?,
        page: Int,
        size: Int
    ): Page<EventoResponse> {
        validaAcesso(dispositivoId, usuarioId)
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100))
        val pageResult = eventoPreparoRepository.findEventosComFiltro(
            dispositivoId,
            resultado?.uppercase(),
            inicio,
            fim,
            pageable
        )
        return pageResult.map { toResponse(it) }
    }

    @Transactional(readOnly = true)
    fun obterEstatisticas(dispositivoId: UUID, usuarioId: UUID): EstatisticasConsumoResponse {
        validaAcesso(dispositivoId, usuarioId)

        val counts = eventoPreparoRepository.countPorOrigem(dispositivoId)
        val porOrigem = mutableMapOf<String, Long>()
        var total = 0L

        for (row in counts) {
            val origem = row[0] as String
            val count = (row[1] as Number).toLong()
            porOrigem[origem] = count
            total += count
        }

        val tempoTotal = eventoPreparoRepository.sumDuracaoConcluidos(dispositivoId)

        return EstatisticasConsumoResponse(
            totalPreparosConcluidos = total,
            porOrigem = porOrigem,
            tempoTotalPreparoSegundos = tempoTotal
        )
    }

    @Transactional(readOnly = true)
    fun obterStatusDescalcificacao(dispositivoId: UUID, usuarioId: UUID): StatusDescalcificacaoResponse {
        validaAcesso(dispositivoId, usuarioId)

        val disp = dispositivoRepository.findById(dispositivoId).orElseThrow {
            ResourceNotFoundException("Dispositivo não encontrado")
        }

        val percentual = if (disp.limiarDescalcificacao > 0) {
            (disp.contadorPreparos.toDouble() / disp.limiarDescalcificacao * 100.0).coerceAtMost(100.0)
        } else 0.0

        return StatusDescalcificacaoResponse(
            contadorPreparos = disp.contadorPreparos,
            limiarDescalcificacao = disp.limiarDescalcificacao,
            precisaDescalcificar = disp.contadorPreparos >= disp.limiarDescalcificacao,
            percentualUso = percentual
        )
    }

    @Transactional
    fun darBaixaDescalcificacao(dispositivoId: UUID, usuarioId: UUID): StatusDescalcificacaoResponse {
        val vinculo = usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(usuarioId, dispositivoId)
            ?: throw ResourceNotFoundException("Dispositivo não encontrado ou você não tem acesso")

        if (vinculo.papel != PapelDispositivo.PROPRIETARIO) {
            throw BadCredentialsException("Apenas o proprietário pode registrar manutenção de descalcificação")
        }

        val disp = vinculo.dispositivo
        disp.contadorPreparos = 0
        disp.atualizadoEm = Instant.now()
        dispositivoRepository.save(disp)

        return obterStatusDescalcificacao(dispositivoId, usuarioId)
    }

    @Transactional
    fun processarProxyBle(dispositivoId: UUID, request: ProxyBleEventosRequest, usuarioId: UUID): List<EventoResponse> {
        validaAcesso(dispositivoId, usuarioId)

        val agora = Instant.now()
        val limitePassado = agora.minus(Duration.ofDays(30))
        val limiteFuturo = agora.plus(Duration.ofMinutes(5))

        val resultados = mutableListOf<EventoResponse>()
        for (eventoReq in request.eventos) {
            if (eventoReq.timestamp.isAfter(limiteFuturo)) {
                throw BadCredentialsException("Timestamp de evento não pode ser no futuro: ${eventoReq.timestamp}")
            }
            if (eventoReq.timestamp.isBefore(limitePassado)) {
                throw BadCredentialsException("Timestamp de evento excede limite de 30 dias no passado: ${eventoReq.timestamp}")
            }

            val salvo = ingestar(dispositivoId, eventoReq)
            if (salvo != null) {
                resultados.add(salvo)
            }
        }

        return resultados
    }

    private fun validaAcesso(dispositivoId: UUID, usuarioId: UUID) {
        usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(usuarioId, dispositivoId)
            ?: throw ResourceNotFoundException("Dispositivo não encontrado ou você não tem acesso")
    }

    private fun toResponse(evento: EventoPreparo): EventoResponse {
        return EventoResponse(
            id = evento.id!!,
            eventoId = evento.eventoId,
            tipo = evento.tipo,
            resultado = evento.resultado,
            origem = evento.origem,
            duracaoS = evento.duracaoS,
            timestamp = evento.timestamp,
            detalheErro = evento.detalheErro,
            criadoEm = evento.criadoEm
        )
    }
}
