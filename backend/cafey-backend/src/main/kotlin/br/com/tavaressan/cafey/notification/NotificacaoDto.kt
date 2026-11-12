package br.com.tavaressan.cafey.notification

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

/**
 * Registro de um destino de push (#360). Android manda `token`; web manda `endpoint`, `p256dh` e
 * `auth` (o `PushSubscription.toJSON()` do navegador). A coerência entre plataforma e campos é
 * validada no serviço.
 */
data class RegistrarPushRequest(
    @field:NotNull(message = "Plataforma é obrigatória")
    val plataforma: PlataformaPush?,

    @field:Size(max = 2048, message = "Token deve ter no máximo 2048 caracteres")
    val token: String? = null,

    @field:Size(max = 2048, message = "Endpoint deve ter no máximo 2048 caracteres")
    val endpoint: String? = null,

    @field:Size(max = 255, message = "p256dh deve ter no máximo 255 caracteres")
    @field:Pattern(regexp = "^[A-Za-z0-9_-]+=*$", message = "p256dh deve estar em Base64URL")
    val p256dh: String? = null,

    @field:Size(max = 255, message = "auth deve ter no máximo 255 caracteres")
    @field:Pattern(regexp = "^[A-Za-z0-9_-]+=*$", message = "auth deve estar em Base64URL")
    val auth: String? = null
)

/** Remoção pelo próprio destino (logout), sem expor o token na URL nem nos logs de acesso. */
data class RemoverPushRequest(
    @field:NotBlank(message = "Destino é obrigatório")
    @field:Size(max = 2048, message = "Destino deve ter no máximo 2048 caracteres")
    val destino: String
)

data class RegistroPushResponse(
    val id: UUID,
    val plataforma: PlataformaPush,
    val criadoEm: Instant,
    val ultimoUsoEm: Instant
)

data class VapidPublicKeyResponse(
    val publicKey: String
)

data class TesteNotificacaoResponse(
    val destinos: Int,
    val entregues: Int
)
