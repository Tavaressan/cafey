package br.com.cafey.schedule

import br.com.cafey.device.Dispositivo
import jakarta.persistence.*
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

@Entity
@Table(name = "agendamentos")
class Agendamento(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispositivo_id", nullable = false)
    var dispositivo: Dispositivo,

    @Column(nullable = false)
    var hora: LocalTime,

    @Column(name = "dias_semana", nullable = false)
    var diasSemana: Short,

    @Column(nullable = false)
    var ativo: Boolean = true,

    @Column(name = "criado_em", nullable = false, updatable = false)
    var criadoEm: Instant = Instant.now(),

    @Column(name = "atualizado_em", nullable = false)
    var atualizadoEm: Instant = Instant.now()
)
