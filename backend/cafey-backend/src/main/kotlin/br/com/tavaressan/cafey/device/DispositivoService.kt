package br.com.cafey.device

import br.com.cafey.exception.BadCredentialsException
import br.com.cafey.exception.ResourceNotFoundException
import br.com.cafey.user.UsuarioRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class DispositivoService(
    private val dispositivoRepository: DispositivoRepository,
    private val usuarioDispositivoRepository: UsuarioDispositivoRepository,
    private val usuarioRepository: UsuarioRepository
) {

    @Transactional(readOnly = true)
    fun listarDoUsuario(usuarioId: UUID): List<DispositivoResponse> {
        val vinculos = usuarioDispositivoRepository.findByUsuarioId(usuarioId)
        return vinculos.map { toResponse(it.dispositivo, it.papel) }
    }

    @Transactional(readOnly = true)
    fun obterPorId(dispositivoId: UUID, usuarioId: UUID): DispositivoResponse {
        val vinculo = usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(usuarioId, dispositivoId)
            ?: throw ResourceNotFoundException("Dispositivo não encontrado ou você não tem acesso")

        return toResponse(vinculo.dispositivo, vinculo.papel)
    }

    @Transactional
    fun criar(request: CriarDispositivoRequest, usuarioId: UUID): DispositivoResponse {
        val usuario = usuarioRepository.findById(usuarioId).orElseThrow {
            ResourceNotFoundException("Usuário não encontrado")
        }

        val dispositivo = Dispositivo(
            nome = request.nome.trim(),
            timezone = request.timezone?.trim() ?: "America/Sao_Paulo",
            duracaoPreparoS = request.duracaoPreparoS ?: 300,
            limiarDescalcificacao = request.limiarDescalcificacao ?: 200
        )
        val salvo = dispositivoRepository.save(dispositivo)

        val vinculo = UsuarioDispositivo(
            usuario = usuario,
            dispositivo = salvo,
            papel = PapelDispositivo.PROPRIETARIO
        )
        usuarioDispositivoRepository.save(vinculo)

        return toResponse(salvo, PapelDispositivo.PROPRIETARIO)
    }

    @Transactional
    fun atualizar(dispositivoId: UUID, request: AtualizarDispositivoRequest, usuarioId: UUID): DispositivoResponse {
        val vinculo = usuarioDispositivoRepository.findByUsuarioIdAndDispositivoId(usuarioId, dispositivoId)
            ?: throw ResourceNotFoundException("Dispositivo não encontrado ou você não tem acesso")

        if (vinculo.papel != PapelDispositivo.PROPRIETARIO) {
            throw BadCredentialsException("Apenas o proprietário pode alterar configurações do dispositivo")
        }

        val disp = vinculo.dispositivo
        request.nome?.let { disp.nome = it.trim() }
        request.timezone?.let { disp.timezone = it.trim() }
        request.duracaoPreparoS?.let { disp.duracaoPreparoS = it }
        request.limiarDescalcificacao?.let { disp.limiarDescalcificacao = it }
        disp.atualizadoEm = Instant.now()

        val atualizado = dispositivoRepository.save(disp)
        return toResponse(atualizado, vinculo.papel)
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

    private fun toResponse(dispositivo: Dispositivo, papel: PapelDispositivo): DispositivoResponse {
        return DispositivoResponse(
            id = dispositivo.id!!,
            nome = dispositivo.nome,
            timezone = dispositivo.timezone,
            estado = dispositivo.estado,
            online = dispositivo.online,
            ultimoVisto = dispositivo.ultimoVisto,
            versaoAgendamentos = dispositivo.versaoAgendamentos,
            duracaoPreparoS = dispositivo.duracaoPreparoS,
            limiarDescalcificacao = dispositivo.limiarDescalcificacao,
            contadorPreparos = dispositivo.contadorPreparos,
            papel = papel,
            criadoEm = dispositivo.criadoEm,
            atualizadoEm = dispositivo.atualizadoEm
        )
    }
}
