package br.com.tavaressan.cafey.schedule

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface AgendamentoRepository : JpaRepository<Agendamento, UUID> {
    fun findByDispositivoId(dispositivoId: UUID): List<Agendamento>

    @Query(
        "SELECT a FROM Agendamento a " +
            "WHERE a.dispositivo.id = :dispositivoId " +
            "AND LOWER(a.descricao) LIKE LOWER(CONCAT('%', :descricao, '%')) " +
            "ORDER BY a.hora"
    )
    fun buscarPorDescricao(dispositivoId: UUID, descricao: String): List<Agendamento>
}
