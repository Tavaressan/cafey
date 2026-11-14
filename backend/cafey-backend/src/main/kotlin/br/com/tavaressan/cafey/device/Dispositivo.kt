package br.com.tavaressan.cafey.device

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "dispositivos")
class Dispositivo(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(nullable = false, length = 255)
    var nome: String,

    @Column(nullable = false, length = 50)
    var timezone: String = "America/Sao_Paulo",

    @Column(nullable = false, length = 50)
    var estado: String = "DESLIGADO",

    @Column(nullable = false)
    var online: Boolean = false,

    @Column(name = "ultimo_visto")
    var ultimoVisto: Instant? = null,

    /** Início do preparo em andamento (estado PREPARANDO), vindo do `desde` do payload MQTT. */
    @Column(name = "preparo_iniciado_em")
    var preparoIniciadoEm: Instant? = null,

    @Column(name = "versao_agendamentos", nullable = false)
    var versaoAgendamentos: Int = 1,

    @Column(name = "duracao_preparo_s", nullable = false)
    var duracaoPreparoS: Int = 300,

    @Column(name = "limiar_descalcificacao", nullable = false)
    var limiarDescalcificacao: Int = 200,

    @Column(name = "contador_preparos", nullable = false)
    var contadorPreparos: Int = 0,

    @Column(name = "rssi")
    var rssi: Int? = null,

    @Column(name = "uptime_s")
    var uptimeS: Long? = null,

    @Column(name = "firmware", length = 100)
    var firmware: String? = null,

    /** Motivo do último reset reportado pelo firmware (ex.: RESET_NORMAL, RESET_WATCHDOG). */
    @Column(name = "ultimo_reset", length = 50)
    var ultimoReset: String? = null,

    /** Resets anormais consecutivos; 3 ou mais indicam modo seguro. */
    @Column(name = "resets_anormais")
    var resetsAnormais: Int? = null,

    /** Temperatura do DHT11 (°C), última reportada com o sensor funcionando. */
    @Column(name = "temperatura_c")
    var temperaturaC: Double? = null,

    /** Umidade relativa do DHT11 (%). */
    @Column(name = "umidade")
    var umidade: Int? = null,

    /** Quando a placa reportou a leitura acima; `null` se nunca reportou ou o sensor falhou. */
    @Column(name = "ambiente_atualizado_em")
    var ambienteAtualizadoEm: Instant? = null,

    /** Código de uso único publicado pela placa na janela de vínculo (FW-24, #275). */
    @Column(name = "codigo_vinculo", length = 16)
    var codigoVinculo: String? = null,

    /** Prazo do código, calculado pelo backend no recebimento do MQTT. */
    @Column(name = "codigo_vinculo_expira_em")
    var codigoVinculoExpiraEm: Instant? = null,

    @Column(name = "criado_em", nullable = false, updatable = false)
    var criadoEm: Instant = Instant.now(),

    @Column(name = "atualizado_em", nullable = false)
    var atualizadoEm: Instant = Instant.now()
)

/** Estados publicados pela placa em que a caldeira (relé) está energizada. */
fun estadoIndicaPreparo(estado: String): Boolean =
    estado.trim().uppercase() in setOf("PREPARANDO", "LIGADO", "BREWING")

/**
 * Estado do relé derivado do estado do módulo: a placa ainda não publica o relé de forma
 * independente, e o relé fica ligado exatamente enquanto o módulo prepara café.
 */
fun estadoReleDerivado(estado: String): String =
    if (estadoIndicaPreparo(estado)) "LIGADO" else "DESLIGADO"
