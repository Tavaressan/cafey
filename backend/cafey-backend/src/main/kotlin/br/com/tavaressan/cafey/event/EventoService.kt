package br.com.tavaressan.cafey.event

import br.com.tavaressan.cafey.device.Dispositivo
import br.com.tavaressan.cafey.device.DispositivoRepository
import br.com.tavaressan.cafey.device.PapelDispositivo
import br.com.tavaressan.cafey.device.UsuarioDispositivo
import br.com.tavaressan.cafey.device.UsuarioDispositivoRepository
import br.com.tavaressan.cafey.exception.BadCredentialsException
import br.com.tavaressan.cafey.exception.ResourceNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

@Service
class EventoService(
    private val eventoPreparoRepository: EventoPreparoRepository,
    private val dispositivoRepository: DispositivoRepository,
    private val usuarioDispositivoRepository: UsuarioDispositivoRepository
) {
    private val logger = LoggerFactory.getLogger(EventoService::class.java)

    companion object {
        // Issue #190 — limiares em preparos dos cuidados sem coluna própria (confirmados pelo dono
        // do projeto). O da descalcificação continua em `Dispositivo.limiarDescalcificacao`.
        const val LIMIAR_ENXAGUE = 40
        const val LIMIAR_FILTRO = 300

        // A baixa de enxágue/filtro é um marcador em eventos_preparo (ver EventoPreparoRepository).
        private const val TIPO_BAIXA_ENXAGUE = "BAIXA_ENXAGUE"
        private const val TIPO_BAIXA_FILTRO = "BAIXA_FILTRO"
        private const val RESULTADO_BAIXA = "BAIXA"
        private val TIPOS_BAIXA = setOf(TIPO_BAIXA_ENXAGUE, TIPO_BAIXA_FILTRO)
    }

    @Transactional
    fun ingestar(dispositivoId: UUID, request: IngestaoEventoRequest): EventoResponse? {
        val dispositivo = dispositivoRepository.findById(dispositivoId).orElse(null) ?: return null

        // Só a baixa autenticada do proprietário cria marcadores; a ingestão (MQTT/proxy BLE, aberta
        // a qualquer usuário vinculado) não pode zerar um contador de cuidado.
        if (request.tipo.uppercase() in TIPOS_BAIXA) {
            logger.warn("Evento com tipo reservado ignorado: dispositivo={}, tipo={}", dispositivoId, request.tipo)
            return null
        }

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

        val dispositivo = dispositivoRepository.findById(dispositivoId).orElseThrow {
            ResourceNotFoundException("Dispositivo não encontrado")
        }
        val timestampsPreparos = eventoPreparoRepository.findTimestampsPreparosConcluidos(dispositivoId)
        val sequenciaManhas = SequenciaManhas.calcular(
            timestampsPreparos,
            ZoneId.of(dispositivo.timezone),
            Instant.now()
        )

        return EstatisticasConsumoResponse(
            totalPreparosConcluidos = total,
            porOrigem = porOrigem,
            tempoTotalPreparoSegundos = tempoTotal,
            sequenciaManhasDias = sequenciaManhas
        )
    }

    @Transactional(readOnly = true)
    fun obterStatusDescalcificacao(dispositivoId: UUID, usuarioId: UUID): StatusDescalcificacaoResponse {
        validaAcesso(dispositivoId, usuarioId)

        val disp = dispositivoRepository.findById(dispositivoId).orElseThrow {
            ResourceNotFoundException("Dispositivo não encontrado")
        }

        return toStatusDescalcificacao(disp)
    }

    @Transactional
    fun darBaixaDescalcificacao(dispositivoId: UUID, usuarioId: UUID): StatusDescalcificacaoResponse {
        val vinculo = vinculoDoProprietario(dispositivoId, usuarioId, "descalcificação")

        val disp = vinculo.dispositivo
        disp.contadorPreparos = 0
        disp.atualizadoEm = Instant.now()
        dispositivoRepository.save(disp)

        return obterStatusDescalcificacao(dispositivoId, usuarioId)
    }

    /** Issue #190 — os três cuidados da tela Cuidados e o destaque (maior fração contador/limiar). */
    @Transactional(readOnly = true)
    fun obterCuidados(dispositivoId: UUID, usuarioId: UUID): CuidadosResponse {
        validaAcesso(dispositivoId, usuarioId)

        val disp = dispositivoRepository.findById(dispositivoId).orElseThrow {
            ResourceNotFoundException("Dispositivo não encontrado")
        }

        val descalcificacao = toStatusDescalcificacao(disp)
        val enxague = statusCuidado(dispositivoId, TIPO_BAIXA_ENXAGUE, LIMIAR_ENXAGUE)
        val filtro = statusCuidado(dispositivoId, TIPO_BAIXA_FILTRO, LIMIAR_FILTRO)

        val destaque = DestaqueCuidado.escolher(
            mapOf(
                TipoCuidado.ENXAGUE to DestaqueCuidado.fracao(enxague.contadorPreparos, enxague.limiarPreparos),
                TipoCuidado.FILTRO to DestaqueCuidado.fracao(filtro.contadorPreparos, filtro.limiarPreparos),
                TipoCuidado.DESCALCIFICACAO to DestaqueCuidado.fracao(
                    descalcificacao.contadorPreparos,
                    descalcificacao.limiarDescalcificacao
                )
            )
        )

        return CuidadosResponse(descalcificacao, enxague, filtro, destaque)
    }

    @Transactional
    fun darBaixaEnxague(dispositivoId: UUID, usuarioId: UUID): StatusCuidadoResponse =
        darBaixaCuidado(dispositivoId, usuarioId, TIPO_BAIXA_ENXAGUE, LIMIAR_ENXAGUE, "enxágue do circuito")

    @Transactional
    fun darBaixaFiltro(dispositivoId: UUID, usuarioId: UUID): StatusCuidadoResponse =
        darBaixaCuidado(dispositivoId, usuarioId, TIPO_BAIXA_FILTRO, LIMIAR_FILTRO, "troca do filtro de água")

    private fun darBaixaCuidado(
        dispositivoId: UUID,
        usuarioId: UUID,
        tipoBaixa: String,
        limiar: Int,
        descricao: String
    ): StatusCuidadoResponse {
        val vinculo = vinculoDoProprietario(dispositivoId, usuarioId, descricao)

        eventoPreparoRepository.save(
            EventoPreparo(
                eventoId = "$tipoBaixa-${UUID.randomUUID()}",
                dispositivo = vinculo.dispositivo,
                tipo = tipoBaixa,
                resultado = RESULTADO_BAIXA,
                origem = "APP",
                duracaoS = 0,
                timestamp = Instant.now()
            )
        )

        // Logo após a baixa nenhum preparo foi ingerido depois do marcador: contador zerado.
        return toStatusCuidado(0, limiar)
    }

    private fun vinculoDoProprietario(dispositivoId: UUID, usuarioId: UUID, descricao: String): UsuarioDispositivo {
        val vinculo = usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(usuarioId, dispositivoId)
            ?: throw ResourceNotFoundException("Dispositivo não encontrado ou você não tem acesso")

        if (vinculo.papel != PapelDispositivo.PROPRIETARIO) {
            throw BadCredentialsException("Apenas o proprietário pode registrar manutenção de $descricao")
        }
        return vinculo
    }

    private fun statusCuidado(dispositivoId: UUID, tipoBaixa: String, limiar: Int): StatusCuidadoResponse {
        val desde = eventoPreparoRepository.findUltimaBaixaEm(dispositivoId, tipoBaixa) ?: Instant.EPOCH
        val contador = eventoPreparoRepository.countPreparosConcluidosDesde(dispositivoId, desde).toInt()
        return toStatusCuidado(contador, limiar)
    }

    private fun toStatusCuidado(contador: Int, limiar: Int) = StatusCuidadoResponse(
        contadorPreparos = contador,
        limiarPreparos = limiar,
        precisaAtencao = contador >= limiar,
        percentualUso = if (limiar > 0) (contador.toDouble() / limiar * 100.0).coerceAtMost(100.0) else 0.0
    )

    private fun toStatusDescalcificacao(disp: Dispositivo): StatusDescalcificacaoResponse {
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
