package br.com.tavaressan.cafey.device

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface DispositivoRepository : JpaRepository<Dispositivo, UUID> {
    @Query(
        "SELECT d FROM Dispositivo d " +
            "WHERE d.id IN (SELECT ud.dispositivo.id FROM UsuarioDispositivo ud WHERE ud.usuario.id = :usuarioId) " +
            "AND LOWER(d.nome) LIKE LOWER(CONCAT('%', :nome, '%')) " +
            "ORDER BY d.nome"
    )
    fun buscarPorNomeDoUsuario(usuarioId: UUID, nome: String): List<Dispositivo>
}
