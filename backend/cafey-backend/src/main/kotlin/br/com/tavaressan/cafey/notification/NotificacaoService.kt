package br.com.tavaressan.cafey.notification

import br.com.tavaressan.cafey.device.DispositivoRepository
import br.com.tavaressan.cafey.device.UsuarioDispositivoRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Transforma eventos da base em notificações e as entrega a todos os destinos dos destinatários (#361).
 *
 * Roda fora da thread da ingestão MQTT (ver [NotificacaoListener]); por isso cada leitura usa a
 * própria transação dos repositórios e nenhuma falha de canal sobe para quem chamou.
 *
 * Deduplicação: o evento de preparo só chega aqui quando a ingestão gravou um `eventoId` novo (#286),
 * e estado/saúde só publicam na transição. Não há outra fonte de repetição além da retentativa por
 * destino, que só ocorre quando o envio anterior falhou.
 */
@Service
class NotificacaoService(
    private val dispositivoRepository: DispositivoRepository,
    private val usuarioDispositivoRepository: UsuarioDispositivoRepository,
    private val registroPushRepository: RegistroPushRepository,
    private val registroPushService: RegistroPushService,
    canais: List<CanalPush>,
    private val properties: NotificacaoProperties,
    private val clock: Clock = Clock.systemUTC(),
    private val pausa: (Long) -> Unit = { Thread.sleep(it) }
) {
    private val logger = LoggerFactory.getLogger(NotificacaoService::class.java)
    private val canaisPorPlataforma = canais.associateBy { it.plataforma }

    companion object {
        const val TEXTO_CAFE_PRONTO = "Seu café está pronto! Enjoy!"
        const val TEXTO_FALHA = "Não foi possível preparar o café"
        const val TEXTO_PREPARO_INICIADO = "Preparando seu café…"
        const val TEXTO_OFFLINE = "A cafeteira ficou offline"
        private const val MAX_DETALHE = 80
    }

    fun notificarEventoPreparo(evento: EventoPreparoRegistradoEvent) {
        val tipo = when (evento.resultado.uppercase()) {
            "CONCLUIDO" -> TipoNotificacao.CAFE_PRONTO
            "ERRO" -> TipoNotificacao.FALHA_PREPARO
            else -> return
        }
        // A fila do firmware guarda eventos enquanto a base está sem rede e os envia depois: um
        // "café pronto" de horas atrás não ajuda ninguém.
        val limite = clock.instant().minus(Duration.ofMinutes(properties.janelaEventoMinutos))
        if (evento.timestamp.isBefore(limite)) {
            logger.info("Evento {} antigo demais para notificar (timestamp={})", evento.eventoId, evento.timestamp)
            return
        }
        val nome = nomeDoDispositivo(evento.dispositivoId) ?: return
        val corpo = if (tipo == TipoNotificacao.CAFE_PRONTO) {
            TEXTO_CAFE_PRONTO
        } else {
            evento.detalheErro?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { "$TEXTO_FALHA: ${it.take(MAX_DETALHE)}" } ?: TEXTO_FALHA
        }
        enviarAosVinculados(evento.dispositivoId, MensagemPush(tipo, nome, corpo, evento.dispositivoId, evento.eventoId))
    }

    fun notificarPreparoIniciado(dispositivoId: UUID) {
        val nome = nomeDoDispositivo(dispositivoId) ?: return
        enviarAosVinculados(
            dispositivoId,
            MensagemPush(TipoNotificacao.PREPARO_INICIADO, nome, TEXTO_PREPARO_INICIADO, dispositivoId)
        )
    }

    /**
     * Chamado depois do debounce. Só avisa se a base continua offline e nada chegou dela desde a
     * queda (`ultimoVisto` não andou); uma volta seguida de nova queda agenda a própria verificação.
     */
    fun verificarOffline(dispositivoId: UUID, ultimoVistoNaQueda: Instant) {
        val dispositivo = dispositivoRepository.findById(dispositivoId).orElse(null) ?: return
        val ultimoVisto = dispositivo.ultimoVisto
        if (dispositivo.online || (ultimoVisto != null && ultimoVisto.isAfter(ultimoVistoNaQueda))) {
            logger.info("Dispositivo {} voltou antes do debounce; aviso de offline descartado", dispositivoId)
            return
        }
        enviarAosVinculados(
            dispositivoId,
            MensagemPush(TipoNotificacao.DISPOSITIVO_OFFLINE, dispositivo.nome, TEXTO_OFFLINE, dispositivoId)
        )
    }

    /** `POST /notificacoes/teste`: confere a configuração ponta a ponta para o próprio usuário. */
    fun enviarTeste(usuarioId: UUID): TesteNotificacaoResponse {
        val mensagem = MensagemPush(TipoNotificacao.TESTE, "Caféy", "Notificações ativadas neste aparelho.")
        val registros = registroPushRepository.findByUsuarioIds(listOf(usuarioId))
        val entregues = registros.count { entregar(it, mensagem) == ResultadoEnvio.ENTREGUE }
        return TesteNotificacaoResponse(destinos = registros.size, entregues = entregues)
    }

    /** Dono e convidados com compartilhamento ativo recebem (decisão do responsável, 12/11/2026). */
    private fun enviarAosVinculados(dispositivoId: UUID, mensagem: MensagemPush) {
        val usuarios = usuarioDispositivoRepository.findUsuarioIdsByDispositivoId(dispositivoId)
        if (usuarios.isEmpty()) return
        val registros = registroPushRepository.findByUsuarioIds(usuarios)
        val entregues = registros.count { entregar(it, mensagem) == ResultadoEnvio.ENTREGUE }
        logger.info(
            "Notificação {} do dispositivo {}: {}/{} destino(s) entregue(s)",
            mensagem.tipo, dispositivoId, entregues, registros.size
        )
    }

    private fun entregar(registro: RegistroPush, mensagem: MensagemPush): ResultadoEnvio {
        val canal = canaisPorPlataforma[registro.plataforma]
        if (canal == null || !canal.habilitado) {
            logger.debug("Canal {} desligado; destino {} ignorado", registro.plataforma, registro.id)
            return ResultadoEnvio.FALHA_PERMANENTE
        }
        val tentativas = properties.tentativas.coerceAtLeast(1)
        var espera = properties.backoffInicialMs
        repeat(tentativas) { i ->
            val resultado = runCatching { canal.enviar(registro, mensagem) }.getOrElse {
                logger.warn("Canal {} lançou exceção ao enviar para {}", registro.plataforma, registro.id, it)
                ResultadoEnvio.FALHA_TRANSITORIA
            }
            when (resultado) {
                ResultadoEnvio.ENTREGUE, ResultadoEnvio.FALHA_PERMANENTE -> return resultado
                ResultadoEnvio.DESTINO_INVALIDO -> {
                    registroPushService.removerInvalido(registro.id!!)
                    return resultado
                }
                ResultadoEnvio.FALHA_TRANSITORIA -> if (i < tentativas - 1) {
                    pausa(espera)
                    espera *= 4
                }
            }
        }
        logger.warn("Destino {} sem entrega após {} tentativa(s)", registro.id, tentativas)
        return ResultadoEnvio.FALHA_TRANSITORIA
    }

    private fun nomeDoDispositivo(dispositivoId: UUID): String? =
        dispositivoRepository.findById(dispositivoId).orElse(null)?.nome
}
