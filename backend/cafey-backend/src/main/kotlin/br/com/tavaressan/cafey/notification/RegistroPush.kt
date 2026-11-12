package br.com.tavaressan.cafey.notification

import br.com.tavaressan.cafey.user.Usuario
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class PlataformaPush {
    ANDROID,
    WEB
}

/**
 * Um destino de push de um usuário (#360): token FCM ou assinatura Web Push.
 *
 * `destino` é o token FCM ou o endpoint da assinatura. As chaves `p256dh`/`auth` só existem no Web
 * Push e cifram o conteúdo para o navegador (RFC 8291). Nenhum desses valores vai para os logs.
 */
@Entity
@Table(
    name = "registros_push",
    uniqueConstraints = [UniqueConstraint(name = "uq_registros_push_destino", columnNames = ["destino"])]
)
class RegistroPush(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    var usuario: Usuario,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var plataforma: PlataformaPush,

    @Column(nullable = false, length = 2048)
    var destino: String,

    @Column(name = "chave_p256dh", length = 255)
    var chaveP256dh: String? = null,

    @Column(name = "chave_auth", length = 255)
    var chaveAuth: String? = null,

    @Column(name = "criado_em", nullable = false, updatable = false)
    var criadoEm: Instant = Instant.now(),

    @Column(name = "ultimo_uso_em", nullable = false)
    var ultimoUsoEm: Instant = Instant.now()
)
