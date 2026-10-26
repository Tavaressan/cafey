package br.com.tavaressan.cafey.device

import br.com.tavaressan.cafey.exception.BadCredentialsException
import br.com.tavaressan.cafey.exception.ResourceNotFoundException
import br.com.tavaressan.cafey.user.UsuarioRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class CompartilhamentoService(
    private val usuarioDispositivoRepository: UsuarioDispositivoRepository,
    private val usuarioRepository: UsuarioRepository
) {

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
