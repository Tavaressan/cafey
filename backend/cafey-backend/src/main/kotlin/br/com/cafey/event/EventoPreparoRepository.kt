package br.com.cafey.event

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface EventoPreparoRepository : JpaRepository<EventoPreparo, UUID> {
    fun existsByDispositivoIdAndEventoId(dispositivoId: UUID, eventoId: String): Boolean

    @Query(
        "SELECT e FROM EventoPreparo e WHERE e.dispositivo.id = :dispositivoId " +
        "AND (:resultado IS NULL OR e.resultado = :resultado) " +
        "AND (:inicio IS NULL OR e.timestamp >= :inicio) " +
        "AND (:fim IS NULL OR e.timestamp <= :fim) " +
        "ORDER BY e.timestamp DESC"
    )
    fun findEventosComFiltro(
        dispositivoId: UUID,
        resultado: String?,
        inicio: Instant?,
        fim: Instant?,
        pageable: Pageable
    ): Page<EventoPreparo>

    @Query("SELECT e.origem as origem, COUNT(e) as count FROM EventoPreparo e WHERE e.dispositivo.id = :dispositivoId AND e.resultado = 'CONCLUIDO' GROUP BY e.origem")
    fun countPorOrigem(dispositivoId: UUID): List<Array<Any>>

    @Query("SELECT COALESCE(SUM(e.duracaoS), 0) FROM EventoPreparo e WHERE e.dispositivo.id = :dispositivoId AND e.resultado = 'CONCLUIDO'")
    fun sumDuracaoConcluidos(dispositivoId: UUID): Long
}
