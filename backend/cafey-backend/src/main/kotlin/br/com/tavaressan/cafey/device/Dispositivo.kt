package br.com.cafey.device

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "dispositivos")
class Dispositivo(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(nullable = false)
    var nome: String,

    @Column(nullable = false)
    var timezone: String = "America/Sao_Paulo",

    @Column(nullable = false)
    var estado: String = "DESLIGADO",

    @Column(nullable = false)
    var online: Boolean = false,

    @Column(name = "ultimo_visto")
    var ultimoVisto: Instant? = null,

    @Column(name = "versao_agendamentos", nullable = false)
    var versaoAgendamentos: Int = 1,

    @Column(name = "duracao_preparo_s", nullable = false)
    var duracaoPreparoS: Int = 300,

    @Column(name = "limiar_descalcificacao", nullable = false)
    var limiarDescalcificacao: Int = 200,

    @Column(name = "contador_preparos", nullable = false)
    var contadorPreparos: Int = 0,

    @Column(name = "criado_em", nullable = false, updatable = false)
    var criadoEm: Instant = Instant.now(),

    @Column(name = "atualizado_em", nullable = false)
    var atualizadoEm: Instant = Instant.now()
)
