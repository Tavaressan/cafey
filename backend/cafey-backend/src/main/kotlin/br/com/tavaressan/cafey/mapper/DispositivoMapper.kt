package br.com.tavaressan.cafey.mapper

import br.com.tavaressan.cafey.device.AtualizarDispositivoRequest
import br.com.tavaressan.cafey.device.CriarDispositivoRequest
import br.com.tavaressan.cafey.device.Dispositivo
import br.com.tavaressan.cafey.device.DispositivoResponse
import br.com.tavaressan.cafey.device.PapelDispositivo
import org.springframework.stereotype.Component
import java.time.Instant

/** Conversões entre os DTOs de [Dispositivo] e a entidade. Não contém regra de negócio. */
@Component
class DispositivoMapper {

    fun toEntity(request: CriarDispositivoRequest): Dispositivo =
        Dispositivo(
            nome = request.nome.trim(),
            timezone = request.timezone?.trim() ?: "America/Sao_Paulo",
            duracaoPreparoS = request.duracaoPreparoS ?: 300,
            limiarDescalcificacao = request.limiarDescalcificacao ?: 200
        )

    /** Aplica só os campos não nulos do [request]; `null` significa "não alterar". */
    fun atualizar(entity: Dispositivo, request: AtualizarDispositivoRequest) {
        request.nome?.let { entity.nome = it.trim() }
        request.timezone?.let { entity.timezone = it.trim() }
        request.duracaoPreparoS?.let { entity.duracaoPreparoS = it }
        request.limiarDescalcificacao?.let { entity.limiarDescalcificacao = it }
        entity.atualizadoEm = Instant.now()
    }

    fun toResponse(entity: Dispositivo, papel: PapelDispositivo): DispositivoResponse =
        DispositivoResponse(
            id = entity.id!!,
            nome = entity.nome,
            timezone = entity.timezone,
            estado = entity.estado,
            online = entity.online,
            ultimoVisto = entity.ultimoVisto,
            versaoAgendamentos = entity.versaoAgendamentos,
            duracaoPreparoS = entity.duracaoPreparoS,
            limiarDescalcificacao = entity.limiarDescalcificacao,
            contadorPreparos = entity.contadorPreparos,
            papel = papel,
            criadoEm = entity.criadoEm,
            atualizadoEm = entity.atualizadoEm
        )
}
