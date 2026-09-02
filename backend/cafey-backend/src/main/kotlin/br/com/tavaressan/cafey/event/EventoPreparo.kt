package br.com.cafey.event

import br.com.cafey.device.Dispositivo
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "eventos_preparo",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_evento_dispositivo", columnNames = ["dispositivo_id", "evento_id"])
    ]
)
class EventoPreparo(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "evento_id", nullable = false)
    var eventoId: String,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispositivo_id", nullable = false)
    var dispositivo: Dispositivo,

    @Column(nullable = false)
    var tipo: String = "PREPARO",

    @Column(nullable = false)
    var resultado: String,

    @Column(nullable = false)
    var origem: String,

    @Column(name = "duracao_s", nullable = false)
    var duracaoS: Int,

    @Column(nullable = false)
    var timestamp: Instant,

    @Column(name = "detalhe_erro")
    var detalheErro: String? = null,

    @Column(name = "criado_em", nullable = false, updatable = false)
    var criadoEm: Instant = Instant.now()
)
