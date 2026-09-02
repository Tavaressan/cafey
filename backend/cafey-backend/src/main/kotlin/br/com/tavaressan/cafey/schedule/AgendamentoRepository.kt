package br.com.cafey.schedule

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface AgendamentoRepository : JpaRepository<Agendamento, UUID> {
    fun findByDispositivoId(dispositivoId: UUID): List<Agendamento>
}
