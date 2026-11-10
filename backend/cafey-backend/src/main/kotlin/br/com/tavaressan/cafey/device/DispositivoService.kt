package br.com.tavaressan.cafey.device

import br.com.tavaressan.cafey.config.AwsIotProperties
import br.com.tavaressan.cafey.event.EventoPreparoRepository
import br.com.tavaressan.cafey.exception.BadCredentialsException
import br.com.tavaressan.cafey.exception.ConflitoException
import br.com.tavaressan.cafey.exception.RequisicaoInvalidaException
import br.com.tavaressan.cafey.exception.ResourceNotFoundException
import br.com.tavaressan.cafey.exception.ServicoIndisponivelException
import br.com.tavaressan.cafey.mapper.DispositivoMapper
import br.com.tavaressan.cafey.mqtt.ComandoPayload
import br.com.tavaressan.cafey.mqtt.MqttClientService
import br.com.tavaressan.cafey.schedule.AgendamentosAlteradosEvent
import br.com.tavaressan.cafey.user.Usuario
import br.com.tavaressan.cafey.user.UsuarioRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

@Service
class DispositivoService(
    private val dispositivoRepository: DispositivoRepository,
    private val usuarioDispositivoRepository: UsuarioDispositivoRepository,
    private val usuarioRepository: UsuarioRepository,
    private val mqttClientService: MqttClientService,
    private val eventPublisher: ApplicationEventPublisher,
    private val dispositivoMapper: DispositivoMapper,
    private val eventoPreparoRepository: EventoPreparoRepository
) {

    @Transactional(readOnly = true)
    fun listarDoUsuario(usuarioId: UUID): List<DispositivoResponse> {
        val vinculos = usuarioDispositivoRepository.findByUsuarioId(usuarioId)
        return vinculos.map { dispositivoMapper.toResponse(it.dispositivo, it.papel) }
    }

    @Transactional(readOnly = true)
    fun buscarPorNome(usuarioId: UUID, nome: String): List<DispositivoResponse> {
        val papeisPorDispositivo = usuarioDispositivoRepository.findByUsuarioId(usuarioId)
            .associate { it.dispositivo.id!! to it.papel }
        return dispositivoRepository.buscarPorNomeDoUsuario(usuarioId, nome).map { dispositivo ->
            dispositivoMapper.toResponse(dispositivo, papeisPorDispositivo.getValue(dispositivo.id!!))
        }
    }

    @Transactional(readOnly = true)
    fun obterPorId(dispositivoId: UUID, usuarioId: UUID): DispositivoResponse {
        val vinculo = usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(usuarioId, dispositivoId)
            ?: throw ResourceNotFoundException("Dispositivo não encontrado ou você não tem acesso")

        return dispositivoMapper.toResponse(vinculo.dispositivo, vinculo.papel)
    }

    @Transactional(readOnly = true)
    fun comandar(dispositivoId: UUID, request: ComandoRequest, usuarioId: UUID): ComandoResponse {
        // Mesmo mascaramento dos demais métodos deste serviço: quem não tem vínculo não descobre
        // se o dispositivo existe.
        val vinculo = usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(usuarioId, dispositivoId)
            ?: throw ResourceNotFoundException("Dispositivo não encontrado ou você não tem acesso")
        val dispositivo = vinculo.dispositivo

        val acao = AcaoComando.entries.find { it.name == request.acao.trim().uppercase() }
            ?: throw RequisicaoInvalidaException(
                "Ação inválida: '${request.acao}'. Valores aceitos: ${AcaoComando.entries.joinToString()}"
            )

        // Duração só faz sentido para ligar o preparo; nos demais casos o firmware ignora o campo.
        val duracaoS = if (acao == AcaoComando.LIGAR) request.duracaoS ?: dispositivo.duracaoPreparoS else 0
        val comandoId = UUID.randomUUID().toString()
        val payload = ComandoPayload(comandoId = comandoId, acao = acao.name, duracaoS = duracaoS)

        // retain = false: comando retido reexecutaria assim que o dispositivo religasse (spec §6.2).
        val topic = AwsIotProperties.topicComando(dispositivoId.toString())
        val publicado = mqttClientService.publish(topic, payload, retain = false)
        if (!publicado) {
            throw ServicoIndisponivelException("Não foi possível enviar o comando: conexão MQTT indisponível")
        }

        return ComandoResponse(comandoId = comandoId, acao = acao, duracaoS = duracaoS, emitidoEm = payload.emitidoEm)
    }

    @Transactional
    fun criar(request: CriarDispositivoRequest, usuarioId: UUID): DispositivoResponse {
        val usuario = usuarioRepository.findById(usuarioId).orElseThrow {
            ResourceNotFoundException("Usuário não encontrado")
        }
        request.serial?.let { return vincularPorSerial(UUID.fromString(it), usuario, request) }

        val salvo = dispositivoRepository.save(dispositivoMapper.toEntity(request))

        val vinculo = UsuarioDispositivo(
            usuario = usuario,
            dispositivo = salvo,
            papel = PapelDispositivo.PROPRIETARIO
        )
        usuarioDispositivoRepository.save(vinculo)

        return dispositivoMapper.toResponse(salvo, PapelDispositivo.PROPRIETARIO)
    }

    /**
     * Cadastro por BLE: o serial é o id que o backend deu à placa (nome da thing no AWS IoT), então
     * só vincula um dispositivo que já existe. Criar com id vindo do app deixaria qualquer conta
     * inventar ids.
     *
     * Quando a placa ainda não tem proprietário, exige o código de uso único da janela de vínculo
     * (FW-24, #275). O código só é aceito dentro do prazo calculado no recebimento do MQTT e é
     * apagado após o primeiro uso. Recadastro pelo mesmo dono continua idempotente e sem código,
     * preservando o comportamento do #266.
     */
    private fun vincularPorSerial(dispositivoId: UUID, usuario: Usuario, request: CriarDispositivoRequest): DispositivoResponse {
        val dispositivo = dispositivoRepository.findById(dispositivoId).orElseThrow {
            ResourceNotFoundException("Cafeteira não reconhecida pelo servidor. Confira se ela foi registrada antes do cadastro.")
        }
        val proprietario = usuarioDispositivoRepository.findByDispositivoId(dispositivoId)
            .find { it.papel == PapelDispositivo.PROPRIETARIO }

        if (proprietario == null) {
            validarCodigoVinculo(dispositivo, request.codigoVinculo)
            dispositivo.nome = request.nome.trim()
            dispositivo.atualizadoEm = Instant.now()
            usuarioDispositivoRepository.save(
                UsuarioDispositivo(usuario = usuario, dispositivo = dispositivo, papel = PapelDispositivo.PROPRIETARIO)
            )
            // Código de uso único: some assim que o vínculo é concluído.
            dispositivo.codigoVinculo = null
            dispositivo.codigoVinculoExpiraEm = null
            dispositivoRepository.save(dispositivo)
        } else if (proprietario.usuario.id != usuario.id) {
            throw ConflitoException("Esta cafeteira já pertence a outra conta.")
        }
        // Mesmo dono: recadastro idempotente; timezone e duração mudam só pelo `atualizar`, que versiona.
        return dispositivoMapper.toResponse(dispositivo, PapelDispositivo.PROPRIETARIO)
    }

    /** Valida o código da janela de vínculo sem vazar, pelo tempo de resposta, qual passo falhou. */
    private fun validarCodigoVinculo(dispositivo: Dispositivo, codigoInformado: String?) {
        val codigoArmazenado = dispositivo.codigoVinculo
        val expiraEm = dispositivo.codigoVinculoExpiraEm

        // Comparação em tempo constante: o código tem 6 dígitos e é curto, mas não custa.
        val confere = codigoArmazenado != null && expiraEm != null && expiraEm.isAfter(Instant.now()) &&
            codigoInformado != null &&
            MessageDigest.isEqual(
                codigoArmazenado.toByteArray(StandardCharsets.UTF_8),
                codigoInformado.toByteArray(StandardCharsets.UTF_8)
            )

        if (!confere) {
            throw RequisicaoInvalidaException(
                "Código de vínculo inválido ou expirado. Abra a janela de vínculo na cafeteira " +
                    "(toque longo no botão) e leia o código pelo app antes de cadastrar."
            )
        }
    }

    @Transactional
    fun atualizar(dispositivoId: UUID, request: AtualizarDispositivoRequest, usuarioId: UUID): DispositivoResponse {
        val vinculo = usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(usuarioId, dispositivoId)
            ?: throw ResourceNotFoundException("Dispositivo não encontrado ou você não tem acesso")

        if (vinculo.papel != PapelDispositivo.PROPRIETARIO) {
            throw BadCredentialsException("Apenas o proprietário pode alterar configurações do dispositivo")
        }

        val disp = vinculo.dispositivo
        // Timezone e duração vão no payload retido de agendamentos (spec §6.2). Mudar qualquer um exige
        // nova versão: a republicação com a mesma versão é descartada pelo firmware (regra `<=`, §5.3).
        // O mapper aplica os campos; a mudança efetiva é detectada comparando com o estado anterior.
        val timezoneAnterior = disp.timezone
        val duracaoAnterior = disp.duracaoPreparoS
        dispositivoMapper.atualizar(disp, request)
        val payloadAgendamentosAlterado =
            disp.timezone != timezoneAnterior || disp.duracaoPreparoS != duracaoAnterior
        if (payloadAgendamentosAlterado) disp.versaoAgendamentos += 1

        val atualizado = dispositivoRepository.save(disp)
        if (payloadAgendamentosAlterado) {
            eventPublisher.publishEvent(AgendamentosAlteradosEvent(dispositivoId))
        }
        return dispositivoMapper.toResponse(atualizado, vinculo.papel)
    }

    @Transactional
    fun excluir(dispositivoId: UUID, usuarioId: UUID) {
        val vinculo = usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(usuarioId, dispositivoId)
            ?: throw ResourceNotFoundException("Dispositivo não encontrado ou você não tem acesso")

        if (vinculo.papel != PapelDispositivo.PROPRIETARIO) {
            throw BadCredentialsException("Apenas o proprietário pode excluir o dispositivo")
        }

        dispositivoRepository.delete(vinculo.dispositivo)
    }

    @Transactional(readOnly = true)
    fun obterDiagnostico(dispositivoId: UUID, usuarioId: UUID): DiagnosticoDispositivoResponse {
        val vinculo = usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(usuarioId, dispositivoId)
            ?: throw ResourceNotFoundException("Dispositivo não encontrado ou você não tem acesso")

        if (vinculo.papel != PapelDispositivo.PROPRIETARIO) {
            throw BadCredentialsException("Apenas o proprietário pode acessar o diagnóstico do dispositivo")
        }

        val dispositivo = vinculo.dispositivo
        val erros = eventoPreparoRepository.findEventosComFiltro(
            dispositivoId,
            "ERRO",
            null,
            null,
            PageRequest.of(0, 5)
        ).content.map {
            ErroRecenteDiagnosticoResponse(
                eventoId = it.eventoId,
                timestamp = it.timestamp,
                origem = it.origem,
                detalheErro = it.detalheErro
            )
        }

        return DiagnosticoDispositivoResponse(
            dispositivoId = dispositivo.id!!,
            online = dispositivo.online,
            ultimoVisto = dispositivo.ultimoVisto,
            estado = dispositivo.estado,
            rssi = dispositivo.rssi,
            firmware = dispositivo.firmware,
            uptimeS = dispositivo.uptimeS,
            versaoAgendamentos = dispositivo.versaoAgendamentos,
            estadoRele = null,
            errosRecentes = erros,
            atualizadoEm = dispositivo.atualizadoEm
        )
    }
}
