package br.com.cafey.security

import br.com.cafey.user.Usuario
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "password_reset_tokens")
class PasswordResetToken(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    var usuario: Usuario,

    @Column(name = "token_hash", nullable = false)
    var tokenHash: String,

    @Column(name = "expira_em", nullable = false)
    var expiraEm: Instant,

    @Column(nullable = false)
    var utilizado: Boolean = false,

    @Column(name = "criado_em", nullable = false, updatable = false)
    var criadoEm: Instant = Instant.now()
)
