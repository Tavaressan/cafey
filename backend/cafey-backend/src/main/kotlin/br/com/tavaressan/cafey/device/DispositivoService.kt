package br.com.tavaressan.cafey.device

import br.com.tavaressan.cafey.config.AwsIotProperties
import br.com.tavaressan.cafey.exception.BadCredentialsException
import br.com.tavaressan.cafey.exception.RequisicaoInvalidaException
import br.com.tavaressan.cafey.exception.ResourceNotFoundException
import br.com.tavaressan.cafey.exception.ServicoIndisponivelException
import br.com.tavaressan.cafey.mapper.DispositivoMapper
import br.com.tavaressan.cafey.mqtt.ComandoPayload
import br.com.tavaressan.cafey.mqtt.MqttClientService
import br.com.tavaressan.cafey.schedule.AgendamentosAlteradosEvent
import br.com.tavaressan.cafey.user.UsuarioRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class DispositivoService(
    private val dispositivoRepository: DispositivoRepository,
    private val usuarioDispositivoRepository: UsuarioDispositivoRepository,
    private val usuarioRepository: UsuarioRepository,
    private val mqttClientService: MqttClientService,
    private val eventPublisher: ApplicationEventPublisher,
    private val dispositivoMapper: DispositivoMapper
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

        val salvo = dispositivoRepository.save(dispositivoMapper.toEntity(request))

        val vinculo = UsuarioDispositivo(
            usuario = usuario,
            dispositivo = salvo,
            papel = PapelDispositivo.PROPRIETARIO
        )
        usuarioDispositivoRepository.save(vinculo)

        return dispositivoMapper.toResponse(salvo, PapelDispositivo.PROPRIETARIO)
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

    @Transactional
    fun compartilhar(dispositivoId: UUID, request: CompartilharDispositivoRequest, usuarioId: UUID): CompartilhamentoResponse {
        val vinculoProprietario = usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(usuarioId, dispositivoId)
            ?: throw ResourceNotFoundException("Dispositivo não encontrado ou você não tem acesso")

        if (vinculoProprietario.papel != PapelDispositivo.PROPRIETARIO) {
            throw BadCredentialsException("Apenas o proprietário pode compartilhar o dispositivo")
        }

        val emailConvidado = request.email.trim().lowercase()
        val convidado = usuarioRepository.findByEmail(emailConvidado)
            ?: throw ResourceNotFoundException("Usuário com email '$emailConvidado' não encontrado")

        if (convidado.id == usuarioId) {
            throw BadCredentialsException("Você já é o proprietário deste dispositivo")
        }

        val vinculoExistente = usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(convidado.id!!, dispositivoId)
        if (vinculoExistente != null) {
            throw BadCredentialsException("O usuário já possui acesso a este dispositivo")
        }

        val novoVinculo = UsuarioDispositivo(
            usuario = convidado,
            dispositivo = vinculoProprietario.dispositivo,
            papel = PapelDispositivo.CONVIDADO
        )
        val salvo = usuarioDispositivoRepository.save(novoVinculo)

        return CompartilhamentoResponse(
            usuarioId = convidado.id!!,
            nome = convidado.nome,
            email = convidado.email,
            papel = salvo.papel,
            compartilhadoEm = salvo.criadoEm
        )
    }

    @Transactional(readOnly = true)
    fun listarCompartilhamentos(dispositivoId: UUID, usuarioId: UUID): List<CompartilhamentoResponse> {
        val vinculo = usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(usuarioId, dispositivoId)
            ?: throw ResourceNotFoundException("Dispositivo não encontrado ou você não tem acesso")

        if (vinculo.papel != PapelDispositivo.PROPRIETARIO) {
            throw BadCredentialsException("Apenas o proprietário pode visualizar os compartilhamentos")
        }

        val vinculos = usuarioDispositivoRepository.findByDispositivoId(dispositivoId)
        return vinculos.map {
            CompartilhamentoResponse(
                usuarioId = it.usuario.id!!,
                nome = it.usuario.nome,
                email = it.usuario.email,
                papel = it.papel,
                compartilhadoEm = it.criadoEm
            )
        }
    }

    @Transactional
    fun removerCompartilhamento(dispositivoId: UUID, convidadoId: UUID, usuarioId: UUID) {
        val vinculoOperador = usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(usuarioId, dispositivoId)
            ?: throw ResourceNotFoundException("Dispositivo não encontrado ou você não tem acesso")

        if (vinculoOperador.papel != PapelDispositivo.PROPRIETARIO && usuarioId != convidadoId) {
            throw BadCredentialsException("Você não tem permissão para remover este compartilhamento")
        }

        val vinculoConvidado = usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(convidadoId, dispositivoId)
            ?: throw ResourceNotFoundException("Compartilhamento não encontrado")

        if (vinculoConvidado.papel == PapelDispositivo.PROPRIETARIO) {
            throw BadCredentialsException("Não é possível remover o vínculo do proprietário")
        }

        usuarioDispositivoRepository.delete(vinculoConvidado)
    }
}
