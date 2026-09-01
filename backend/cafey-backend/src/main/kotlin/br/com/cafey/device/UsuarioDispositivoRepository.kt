package br.com.cafey.device

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UsuarioDispositivoRepository : JpaRepository<UsuarioDispositivo, UsuarioDispositivoId> {
    @Query("SELECT ud FROM UsuarioDispositivo ud JOIN FETCH ud.dispositivo WHERE ud.usuario.id = :usuarioId")
    fun findByUsuarioId(usuarioId: UUID): List<UsuarioDispositivo>

    @Query("SELECT ud FROM UsuarioDispositivo ud JOIN FETCH ud.usuario WHERE ud.dispositivo.id = :dispositivoId")
    fun findByDispositivoId(dispositivoId: UUID): List<UsuarioDispositivo>

    fun findByUsuarioIdAndDispositivoId(usuarioId: UUID, dispositivoId: UUID): UsuarioDispositivo?
}
