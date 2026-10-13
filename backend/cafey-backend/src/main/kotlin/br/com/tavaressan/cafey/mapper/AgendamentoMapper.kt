package br.com.tavaressan.cafey.mapper

import br.com.tavaressan.cafey.device.Dispositivo
import br.com.tavaressan.cafey.schedule.Agendamento
import br.com.tavaressan.cafey.schedule.AgendamentoResponse
import br.com.tavaressan.cafey.schedule.AtualizarAgendamentoRequest
import br.com.tavaressan.cafey.schedule.CriarAgendamentoRequest
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Conversões entre os DTOs de [Agendamento] e a entidade. Não contém regra de negócio. */
@Component
class AgendamentoMapper {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun toEntity(request: CriarAgendamentoRequest, dispositivo: Dispositivo): Agendamento =
        Agendamento(
            dispositivo = dispositivo,
            hora = LocalTime.parse(request.hora, timeFormatter),
            diasSemana = request.diasSemana,
            ativo = request.ativo,
            duracaoPreparoS = request.duracaoPreparoS
        )

    /** Aplica só os campos não nulos do [request]; `null` significa "não alterar". */
    fun atualizar(entity: Agendamento, request: AtualizarAgendamentoRequest) {
        request.hora?.let { entity.hora = LocalTime.parse(it, timeFormatter) }
        request.diasSemana?.let { entity.diasSemana = it }
        request.ativo?.let { entity.ativo = it }
        request.duracaoPreparoS?.let { entity.duracaoPreparoS = it }
        entity.atualizadoEm = Instant.now()
    }

    fun toResponse(entity: Agendamento): AgendamentoResponse =
        AgendamentoResponse(
            id = entity.id!!,
            // Seguro sem JOIN FETCH: o Hibernate resolve @Id a partir da FK já carregada na entidade
            // dona (agendamento), sem precisar inicializar o proxy de "dispositivo". Isso vale para o
            // proxy padrão baseado em subclasse; deixaria de valer sob bytecode enhancement, que
            // intercepta o getter e forçaria a inicialização completa do proxy.
            dispositivoId = entity.dispositivo.id!!,
            hora = entity.hora.format(timeFormatter),
            diasSemana = entity.diasSemana,
            ativo = entity.ativo,
            duracaoPreparoS = entity.duracaoPreparoS,
            criadoEm = entity.criadoEm,
            atualizadoEm = entity.atualizadoEm
        )
}
