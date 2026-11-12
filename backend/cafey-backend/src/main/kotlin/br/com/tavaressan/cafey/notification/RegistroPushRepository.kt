package br.com.tavaressan.cafey.notification

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface RegistroPushRepository : JpaRepository<RegistroPush, UUID> {
    fun findByDestino(destino: String): RegistroPush?

    fun findByIdAndUsuarioId(id: UUID, usuarioId: UUID): RegistroPush?

    fun findByUsuarioIdOrderByUltimoUsoEmDesc(usuarioId: UUID): List<RegistroPush>

    @Query("SELECT r FROM RegistroPush r WHERE r.usuario.id IN :usuarioIds")
    fun findByUsuarioIds(usuarioIds: Collection<UUID>): List<RegistroPush>

    @Modifying
    @Query("DELETE FROM RegistroPush r WHERE r.destino = :destino AND r.usuario.id = :usuarioId")
    fun deleteByDestinoAndUsuarioId(destino: String, usuarioId: UUID): Int

    @Modifying
    @Query("DELETE FROM RegistroPush r WHERE r.ultimoUsoEm < :limite")
    fun deleteByUltimoUsoEmBefore(limite: Instant): Int
}
