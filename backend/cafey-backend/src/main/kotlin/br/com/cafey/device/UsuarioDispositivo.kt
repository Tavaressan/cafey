package br.com.cafey.device

import br.com.cafey.user.Usuario
import jakarta.persistence.*
import java.io.Serializable
import java.time.Instant
import java.util.UUID

data class UsuarioDispositivoId(
    var usuario: UUID? = null,
    var dispositivo: UUID? = null
) : Serializable

@Entity
@Table(name = "usuario_dispositivo")
@IdClass(UsuarioDispositivoId::class)
class UsuarioDispositivo(
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    var usuario: Usuario,

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispositivo_id", nullable = false)
    var dispositivo: Dispositivo,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var papel: PapelDispositivo = PapelDispositivo.PROPRIETARIO,

    @Column(name = "criado_em", nullable = false, updatable = false)
    var criadoEm: Instant = Instant.now()
)
