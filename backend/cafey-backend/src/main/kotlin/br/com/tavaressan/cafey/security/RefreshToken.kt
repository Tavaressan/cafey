package br.com.cafey.security

import br.com.cafey.user.Usuario
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    var usuario: Usuario,

    @Column(name = "token_hash", nullable = false, unique = true)
    var tokenHash: String,

    @Column(name = "familia_id", nullable = false)
    var familiaId: UUID,

    @Column(nullable = false)
    var revogado: Boolean = false,

    @Column(name = "expira_em", nullable = false)
    var expiraEm: Instant,

    @Column(name = "criado_em", nullable = false, updatable = false)
    var criadoEm: Instant = Instant.now()
)
