package br.com.tavaressan.cafey.schedule

import br.com.tavaressan.cafey.device.DispositivoRepository
import br.com.tavaressan.cafey.device.PapelDispositivo
import br.com.tavaressan.cafey.device.UsuarioDispositivoRepository
import br.com.tavaressan.cafey.exception.BadCredentialsException
import br.com.tavaressan.cafey.exception.ResourceNotFoundException
import br.com.tavaressan.cafey.mapper.AgendamentoMapper
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class AgendamentosAlteradosEvent(val dispositivoId: UUID)

@Service
class AgendamentoService(
    private val agendamentoRepository: AgendamentoRepository,
    private val dispositivoRepository: DispositivoRepository,
    private val usuarioDispositivoRepository: UsuarioDispositivoRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val agendamentoMapper: AgendamentoMapper
) {

    @Transactional(readOnly = true)
    fun listar(dispositivoId: UUID, usuarioId: UUID): List<AgendamentoResponse> {
        validaAcesso(dispositivoId, usuarioId)
        return agendamentoRepository.findByDispositivoId(dispositivoId).map { agendamentoMapper.toResponse(it) }
    }

    @Transactional(readOnly = true)
    fun buscarPorDescricao(dispositivoId: UUID, usuarioId: UUID, descricao: String): List<AgendamentoResponse> {
        validaAcesso(dispositivoId, usuarioId)
        return agendamentoRepository.buscarPorDescricao(dispositivoId, descricao).map { agendamentoMapper.toResponse(it) }
    }

    @Transactional(readOnly = true)
    fun obterPorId(dispositivoId: UUID, agendamentoId: UUID, usuarioId: UUID): AgendamentoResponse {
        validaAcesso(dispositivoId, usuarioId)
        return agendamentoMapper.toResponse(buscarDoDispositivo(dispositivoId, agendamentoId))
    }

    @Transactional
    fun criar(dispositivoId: UUID, request: CriarAgendamentoRequest, usuarioId: UUID): AgendamentoResponse {
        validaAcesso(dispositivoId, usuarioId)

        val dispositivo = dispositivoRepository.findById(dispositivoId).orElseThrow {
            ResourceNotFoundException("Dispositivo não encontrado")
        }

        val salvo = agendamentoRepository.save(agendamentoMapper.toEntity(request, dispositivo))

        // Incremento monotônico de versão
        dispositivo.versaoAgendamentos += 1
        dispositivo.atualizadoEm = Instant.now()
        dispositivoRepository.save(dispositivo)

        eventPublisher.publishEvent(AgendamentosAlteradosEvent(dispositivoId))

        return agendamentoMapper.toResponse(salvo)
    }

    @Transactional
    fun atualizar(dispositivoId: UUID, agendamentoId: UUID, request: AtualizarAgendamentoRequest, usuarioId: UUID): AgendamentoResponse {
        validaAcesso(dispositivoId, usuarioId)

        val agendamento = buscarDoDispositivo(dispositivoId, agendamentoId)

        agendamentoMapper.atualizar(agendamento, request)

        val salvo = agendamentoRepository.save(agendamento)

        val disp = agendamento.dispositivo
        disp.versaoAgendamentos += 1
        disp.atualizadoEm = Instant.now()
        dispositivoRepository.save(disp)

        eventPublisher.publishEvent(AgendamentosAlteradosEvent(dispositivoId))

        return agendamentoMapper.toResponse(salvo)
    }

    @Transactional
    fun excluir(dispositivoId: UUID, agendamentoId: UUID, usuarioId: UUID) {
        validaAcesso(dispositivoId, usuarioId)

        val agendamento = buscarDoDispositivo(dispositivoId, agendamentoId)

        val disp = agendamento.dispositivo
        agendamentoRepository.delete(agendamento)

        disp.versaoAgendamentos += 1
        disp.atualizadoEm = Instant.now()
        dispositivoRepository.save(disp)

        eventPublisher.publishEvent(AgendamentosAlteradosEvent(dispositivoId))
    }

    private fun buscarDoDispositivo(dispositivoId: UUID, agendamentoId: UUID): Agendamento {
        val agendamento = agendamentoRepository.findById(agendamentoId).orElseThrow {
            ResourceNotFoundException("Agendamento não encontrado")
        }

        if (agendamento.dispositivo.id != dispositivoId) {
            throw ResourceNotFoundException("Agendamento não pertence a este dispositivo")
        }
        return agendamento
    }

    private fun validaAcesso(dispositivoId: UUID, usuarioId: UUID) {
        val vinculo = usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(usuarioId, dispositivoId)
            ?: throw ResourceNotFoundException("Dispositivo não encontrado ou você não tem acesso")
    }
}
