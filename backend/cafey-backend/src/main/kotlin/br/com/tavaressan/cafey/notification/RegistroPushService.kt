package br.com.tavaressan.cafey.notification

import br.com.tavaressan.cafey.exception.RequisicaoInvalidaException
import br.com.tavaressan.cafey.exception.ResourceNotFoundException
import br.com.tavaressan.cafey.user.UsuarioRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

/** Cadastro dos destinos de push de cada usuário (#360). */
@Service
class RegistroPushService(
    private val registroPushRepository: RegistroPushRepository,
    private val usuarioRepository: UsuarioRepository,
    private val properties: NotificacaoProperties
) {
    private val logger = LoggerFactory.getLogger(RegistroPushService::class.java)

    /**
     * Idempotente: o mesmo token/endpoint atualiza o registro existente, inclusive trocando o dono
     * (outro usuário entrou no mesmo aparelho sem o logout anterior ter removido o token).
     */
    @Transactional
    fun registrar(usuarioId: UUID, request: RegistrarPushRequest): RegistroPushResponse {
        val plataforma = request.plataforma ?: throw RequisicaoInvalidaException("Plataforma é obrigatória")
        val destino = when (plataforma) {
            PlataformaPush.ANDROID -> validarToken(request)
            PlataformaPush.WEB -> validarAssinatura(request)
        }
        val usuario = usuarioRepository.findById(usuarioId).orElseThrow {
            ResourceNotFoundException("Usuário não encontrado")
        }

        val agora = Instant.now()
        val registro = registroPushRepository.findByDestino(destino)?.also {
            it.usuario = usuario
            it.plataforma = plataforma
            it.ultimoUsoEm = agora
        } ?: RegistroPush(usuario = usuario, plataforma = plataforma, destino = destino, criadoEm = agora, ultimoUsoEm = agora)
        if (plataforma == PlataformaPush.WEB) {
            registro.chaveP256dh = request.p256dh!!.trimEnd('=')
            registro.chaveAuth = request.auth!!.trimEnd('=')
        } else {
            registro.chaveP256dh = null
            registro.chaveAuth = null
        }
        val salvo = registroPushRepository.save(registro)
        aplicarLimite(usuarioId)
        logger.info("Destino de push {} registrado: usuario={}, plataforma={}", salvo.id, usuarioId, plataforma)
        return toResponse(salvo)
    }

    @Transactional(readOnly = true)
    fun listar(usuarioId: UUID): List<RegistroPushResponse> =
        registroPushRepository.findByUsuarioIdOrderByUltimoUsoEmDesc(usuarioId).map(::toResponse)

    @Transactional
    fun remover(usuarioId: UUID, id: UUID) {
        val registro = registroPushRepository.findByIdAndUsuarioId(id, usuarioId)
            ?: throw ResourceNotFoundException("Registro de push não encontrado")
        registroPushRepository.delete(registro)
    }

    /** Logout: remove pelo token/endpoint. Idempotente, e só apaga registro do próprio usuário. */
    @Transactional
    fun removerPorDestino(usuarioId: UUID, destino: String) {
        registroPushRepository.deleteByDestinoAndUsuarioId(destino.trim(), usuarioId)
    }

    /** Chamado pelo despacho quando o serviço de push diz que o destino morreu. */
    @Transactional
    fun removerInvalido(id: UUID) {
        if (registroPushRepository.existsById(id)) {
            registroPushRepository.deleteById(id)
            logger.info("Destino de push {} removido: o serviço de push o considera inválido", id)
        }
    }

    /** Os apps re-registram a cada login/abertura; o que não foi renovado no prazo é lixo. */
    @Scheduled(cron = "0 17 4 * * *")
    @Transactional
    fun expirarRegistrosAntigos() {
        val limite = Instant.now().minus(Duration.ofDays(properties.expiracaoRegistroDias))
        val removidos = registroPushRepository.deleteByUltimoUsoEmBefore(limite)
        if (removidos > 0) {
            logger.info("{} destino(s) de push expirado(s) removido(s)", removidos)
        }
    }

    private fun aplicarLimite(usuarioId: UUID) {
        val excedentes = registroPushRepository.findByUsuarioIdOrderByUltimoUsoEmDesc(usuarioId)
            .drop(properties.maxRegistrosPorUsuario.coerceAtLeast(1))
        if (excedentes.isNotEmpty()) {
            registroPushRepository.deleteAll(excedentes)
        }
    }

    private fun validarToken(request: RegistrarPushRequest): String {
        val token = request.token?.trim()
        if (token.isNullOrEmpty()) {
            throw RequisicaoInvalidaException("Token é obrigatório para a plataforma ANDROID")
        }
        if (token.any { it.isWhitespace() }) {
            throw RequisicaoInvalidaException("Token inválido")
        }
        return token
    }

    private fun validarAssinatura(request: RegistrarPushRequest): String {
        val endpoint = request.endpoint?.trim()
        if (endpoint.isNullOrEmpty()) {
            throw RequisicaoInvalidaException("Endpoint é obrigatório para a plataforma WEB")
        }
        if (!endpointPermitido(endpoint)) {
            throw RequisicaoInvalidaException("Endpoint de push não suportado")
        }
        val p256dh = decodificar(request.p256dh, "p256dh")
        if (p256dh.size != 65 || p256dh[0] != 0x04.toByte()) {
            throw RequisicaoInvalidaException("p256dh deve ser um ponto P-256 não comprimido (65 bytes)")
        }
        if (decodificar(request.auth, "auth").size != 16) {
            throw RequisicaoInvalidaException("auth deve ter 16 bytes")
        }
        return endpoint
    }

    /** Barra SSRF: o backend só faz POST para serviços de push conhecidos. */
    fun endpointPermitido(endpoint: String): Boolean {
        val uri = runCatching { URI(endpoint) }.getOrNull() ?: return false
        val esquemaOk = uri.scheme == "https" || (properties.webpushPermitirHttp && uri.scheme == "http")
        val host = uri.host?.lowercase() ?: return false
        if (!esquemaOk || uri.userInfo != null) return false
        return properties.webpushHostsPermitidos.any { permitido ->
            val p = permitido.trim().lowercase()
            if (p.startsWith(".")) host.endsWith(p) else host == p
        }
    }

    private fun decodificar(valor: String?, campo: String): ByteArray {
        if (valor.isNullOrBlank()) {
            throw RequisicaoInvalidaException("$campo é obrigatório para a plataforma WEB")
        }
        return runCatching { Base64.getUrlDecoder().decode(valor.trim().trimEnd('=')) }
            .getOrElse { throw RequisicaoInvalidaException("$campo deve estar em Base64URL") }
    }

    private fun toResponse(r: RegistroPush) = RegistroPushResponse(
        id = r.id!!,
        plataforma = r.plataforma,
        criadoEm = r.criadoEm,
        ultimoUsoEm = r.ultimoUsoEm
    )
}
