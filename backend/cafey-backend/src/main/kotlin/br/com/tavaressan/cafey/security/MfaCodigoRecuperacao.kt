package br.com.tavaressan.cafey.security

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

/** Código de recuperação do 2FA, de uso único. Só o hash SHA-256 é guardado. */
@Entity
@Table(
    name = "mfa_codigos_recuperacao",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_mfa_codigos_recuperacao", columnNames = ["usuario_id", "codigo_hash"])
    ]
)
class MfaCodigoRecuperacao(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "usuario_id", nullable = false)
    var usuarioId: UUID,

    @Column(name = "codigo_hash", nullable = false, length = 64)
    var codigoHash: String,

    @Column(name = "criado_em", nullable = false, updatable = false)
    var criadoEm: Instant = Instant.now()
)

interface MfaCodigoRecuperacaoRepository : JpaRepository<MfaCodigoRecuperacao, UUID> {

    /** Remove o código se ele existir; o retorno 1 indica que foi consumido agora (atômico). */
    @Modifying
    @Query("delete from MfaCodigoRecuperacao c where c.usuarioId = :usuarioId and c.codigoHash = :codigoHash")
    fun consumir(@Param("usuarioId") usuarioId: UUID, @Param("codigoHash") codigoHash: String): Int

    @Modifying
    @Query("delete from MfaCodigoRecuperacao c where c.usuarioId = :usuarioId")
    fun removerTodos(@Param("usuarioId") usuarioId: UUID): Int

    fun countByUsuarioId(usuarioId: UUID): Long
}
