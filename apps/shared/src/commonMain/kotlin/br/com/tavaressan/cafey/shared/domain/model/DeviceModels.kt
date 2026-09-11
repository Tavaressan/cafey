package br.com.tavaressan.cafey.shared.domain.model

import kotlinx.serialization.Serializable

/**
 * Modelos de dispositivo — espelham `br.com.tavaressan.cafey.device.DeviceDto` e
 * `PapelDispositivo` do backend. Timestamps chegam como `String` ISO-8601 (é como o Jackson do
 * Spring serializa `java.time.Instant` por padrão) — sem `kotlinx-datetime` no catálogo, não vale
 * a pena introduzir a dependência só para formatar data.
 */
@Serializable
data class CriarDispositivoRequest(
    val nome: String,
    val timezone: String? = null,
    val duracaoPreparoS: Int? = null,
    val limiarDescalcificacao: Int? = null,
)

@Serializable
data class AtualizarDispositivoRequest(
    val nome: String? = null,
    val timezone: String? = null,
    val duracaoPreparoS: Int? = null,
    val limiarDescalcificacao: Int? = null,
)

@Serializable
data class CompartilharDispositivoRequest(
    val email: String,
)

@Serializable
enum class PapelDispositivo {
    PROPRIETARIO,
    CONVIDADO,
}

@Serializable
data class DispositivoResponse(
    val id: String,
    val nome: String,
    val timezone: String,
    val estado: String,
    val online: Boolean,
    val ultimoVisto: String? = null,
    val versaoAgendamentos: Int,
    val duracaoPreparoS: Int,
    val limiarDescalcificacao: Int,
    val contadorPreparos: Int,
    val papel: PapelDispositivo,
    val criadoEm: String,
    val atualizadoEm: String,
)

@Serializable
data class CompartilhamentoResponse(
    val usuarioId: String,
    val nome: String,
    val email: String,
    val papel: PapelDispositivo,
    val compartilhadoEm: String,
)

/**
 * O backend guarda `estado` como texto livre (o firmware manda o que quiser — ver
 * `Dispositivo.kt`, default `"DESLIGADO"`, e `MqttIngestionService.processarEstado`, que grava o
 * payload sem validar). A spec (§6.2) descreve os três estados em prosa — ocioso, preparando, erro
 * — mas não fixa a grafia exata que o firmware envia. `DeviceState` normaliza (case-insensitive)
 * os textos conhecidos para a UI e cai em `Unknown` para o que não reconhece, em vez de quebrar.
 */
sealed interface DeviceState {
    data object Idle : DeviceState
    data object Brewing : DeviceState
    data object Error : DeviceState
    data class Unknown(val raw: String) : DeviceState

    companion object {
        fun from(estado: String): DeviceState = when (estado.trim().uppercase()) {
            "OCIOSO", "DESLIGADO", "IDLE" -> Idle
            "PREPARANDO", "LIGADO", "BREWING" -> Brewing
            "ERRO", "ERROR" -> Error
            else -> Unknown(estado)
        }
    }
}
