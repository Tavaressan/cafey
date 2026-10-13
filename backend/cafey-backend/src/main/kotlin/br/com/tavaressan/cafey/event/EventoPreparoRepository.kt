package br.com.tavaressan.cafey.event

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

    // Issue #190 — marcadores de baixa de cuidado (tipo BAIXA_*) ficam fora do histórico de preparos.
    @Query(
        "SELECT e FROM EventoPreparo e WHERE e.dispositivo.id = :dispositivoId AND e.tipo = 'PREPARO' " +
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

    // Issue #177 — timestamps dos preparos concluídos, para o cálculo de "sequência de manhãs"
    // (SequenciaManhas.calcular). Só o timestamp é necessário, sem carregar a entidade inteira.
    @Query("SELECT e.timestamp FROM EventoPreparo e WHERE e.dispositivo.id = :dispositivoId AND e.tipo = 'PREPARO' AND e.resultado = 'CONCLUIDO'")
    fun findTimestampsPreparosConcluidos(dispositivoId: UUID): List<Instant>

    // Issue #190 — contadores de enxágue e filtro derivados da própria tabela, sem coluna nova: a
    // baixa é um marcador (tipo BAIXA_*, resultado BAIXA) e o contador conta os preparos concluídos
    // ingeridos depois dele. `criadoEm` (hora de ingestão) em vez de `timestamp` (hora do
    // dispositivo, que pode ser provisória antes do NTP): mesma semântica do contador de
    // descalcificação, que zera na baixa e conta a partir do que chega depois.
    @Query("SELECT MAX(e.criadoEm) FROM EventoPreparo e WHERE e.dispositivo.id = :dispositivoId AND e.tipo = :tipo AND e.resultado = 'BAIXA'")
    fun findUltimaBaixaEm(dispositivoId: UUID, tipo: String): Instant?

    @Query("SELECT COUNT(e) FROM EventoPreparo e WHERE e.dispositivo.id = :dispositivoId AND e.tipo = 'PREPARO' AND e.resultado = 'CONCLUIDO' AND e.criadoEm > :desde")
    fun countPreparosConcluidosDesde(dispositivoId: UUID, desde: Instant): Long
}
