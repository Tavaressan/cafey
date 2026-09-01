package br.com.cafey.schedule

import br.com.cafey.device.DispositivoRepository
import br.com.cafey.device.PapelDispositivo
import br.com.cafey.device.UsuarioDispositivoRepository
import br.com.cafey.exception.BadCredentialsException
import br.com.cafey.exception.ResourceNotFoundException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

data class AgendamentosAlteradosEvent(val dispositivoId: UUID)

@Service
class AgendamentoService(
    private val agendamentoRepository: AgendamentoRepository,
    private val dispositivoRepository: DispositivoRepository,
    private val usuarioDispositivoRepository: UsuarioDispositivoRepository,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    @Transactional(readOnly = true)
    fun listar(dispositivoId: UUID, usuarioId: UUID): List<AgendamentoResponse> {
        validaAcesso(dispositivoId, usuarioId)
        return agendamentoRepository.findByDispositivoId(dispositivoId).map { toResponse(it) }
    }

    @Transactional
    fun criar(dispositivoId: UUID, request: CriarAgendamentoRequest, usuarioId: UUID): AgendamentoResponse {
        validaAcesso(dispositivoId, usuarioId)

        val dispositivo = dispositivoRepository.findById(dispositivoId).orElseThrow {
            ResourceNotFoundException("Dispositivo não encontrado")
        }

        val agendamento = Agendamento(
            dispositivo = dispositivo,
            hora = LocalTime.parse(request.hora, timeFormatter),
            diasSemana = request.diasSemana,
            ativo = request.ativo
        )
        val salvo = agendamentoRepository.save(agendamento)

        // Incremento monotônico de versão
        dispositivo.versaoAgendamentos += 1
        dispositivo.atualizadoEm = Instant.now()
        dispositivoRepository.save(dispositivo)

        eventPublisher.publishEvent(AgendamentosAlteradosEvent(dispositivoId))

        return toResponse(salvo)
    }

    @Transactional
    fun atualizar(dispositivoId: UUID, agendamentoId: UUID, request: AtualizarAgendamentoRequest, usuarioId: UUID): AgendamentoResponse {
        validaAcesso(dispositivoId, usuarioId)

        val agendamento = agendamentoRepository.findById(agendamentoId).orElseThrow {
            ResourceNotFoundException("Agendamento não encontrado")
        }

        if (agendamento.dispositivo.id != dispositivoId) {
            throw ResourceNotFoundException("Agendamento não pertence a este dispositivo")
        }

        request.hora?.let { agendamento.hora = LocalTime.parse(it, timeFormatter) }
        request.diasSemana?.let { agendamento.diasSemana = it }
        request.ativo?.let { agendamento.ativo = it }
        agendamento.atualizadoEm = Instant.now()

        val salvo = agendamentoRepository.save(agendamento)

        val disp = agendamento.dispositivo
        disp.versaoAgendamentos += 1
        disp.atualizadoEm = Instant.now()
        dispositivoRepository.save(disp)

        eventPublisher.publishEvent(AgendamentosAlteradosEvent(dispositivoId))

        return toResponse(salvo)
    }

    @Transactional
    fun excluir(dispositivoId: UUID, agendamentoId: UUID, usuarioId: UUID) {
        validaAcesso(dispositivoId, usuarioId)

        val agendamento = agendamentoRepository.findById(agendamentoId).orElseThrow {
            ResourceNotFoundException("Agendamento não encontrado")
        }

        if (agendamento.dispositivo.id != dispositivoId) {
            throw ResourceNotFoundException("Agendamento não pertence a este dispositivo")
        }

        val disp = agendamento.dispositivo
        agendamentoRepository.delete(agendamento)

        disp.versaoAgendamentos += 1
        disp.atualizadoEm = Instant.now()
        dispositivoRepository.save(disp)

        eventPublisher.publishEvent(AgendamentosAlteradosEvent(dispositivoId))
    }

    private fun validaAcesso(dispositivoId: UUID, usuarioId: UUID) {
        val vinculo = usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(usuarioId, dispositivoId)
            ?: throw ResourceNotFoundException("Dispositivo não encontrado ou você não tem acesso")
    }

    private fun toResponse(agendamento: Agendamento): AgendamentoResponse {
        return AgendamentoResponse(
            id = agendamento.id!!,
            dispositivoId = agendamento.dispositivo.id!!,
            hora = agendamento.hora.format(timeFormatter),
            diasSemana = agendamento.diasSemana,
            ativo = agendamento.ativo,
            criadoEm = agendamento.criadoEm,
            atualizadoEm = agendamento.atualizadoEm
        )
    }
}
