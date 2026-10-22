package br.com.tavaressan.cafey.device

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

// Tamanhos das colunas `nome` e `timezone` de `dispositivos` (migration V3). Os limites de duração
// repetem os de ComandoRequest (teto de segurança de 900s).
private const val NOME_MAX = 255
private const val TIMEZONE_MAX = 50

// Aceita `null` e recusa texto vazio ou só de espaços. Substitui o @NotBlank nos campos opcionais do
// PUT, onde `null` significa "não alterar". O `(?s)` (DOTALL) é necessário: o Hibernate Validator usa
// `matches()`, e sem ele o `.` não casaria quebra de linha, que o @NotBlank do POST aceita.
private const val NAO_EM_BRANCO = "(?s).*\\S.*"

// UUID canônico: o serial que o app lê da placa por BLE é o id do dispositivo e o nome da thing.
private const val UUID_CANONICO = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"

data class CriarDispositivoRequest(
    @field:NotBlank(message = "Nome do dispositivo é obrigatório")
    @field:Size(max = NOME_MAX, message = "Nome do dispositivo deve ter no máximo 255 caracteres")
    val nome: String,

    @field:Size(max = TIMEZONE_MAX, message = "Timezone deve ter no máximo 50 caracteres")
    @field:Pattern(regexp = NAO_EM_BRANCO, message = "Timezone não pode ser vazio")
    @field:TimezoneSuportado
    val timezone: String? = null,

    @field:Min(value = 30, message = "Duração de preparo deve ter no mínimo 30s")
    @field:Max(value = 900, message = "Duração de preparo deve ter no máximo 900s")
    val duracaoPreparoS: Int? = null,

    @field:Positive(message = "Limiar de descalcificação deve ser maior que zero")
    val limiarDescalcificacao: Int? = null,

    /** Serial lido da placa no cadastro por BLE. Quando presente, vincula a placa em vez de criar outra. */
    @field:Pattern(regexp = UUID_CANONICO, message = "Serial da cafeteira inválido")
    val serial: String? = null
)

data class AtualizarDispositivoRequest(
    @field:Size(max = NOME_MAX, message = "Nome do dispositivo deve ter no máximo 255 caracteres")
    @field:Pattern(regexp = NAO_EM_BRANCO, message = "Nome do dispositivo não pode ser vazio")
    val nome: String? = null,

    @field:Size(max = TIMEZONE_MAX, message = "Timezone deve ter no máximo 50 caracteres")
    @field:Pattern(regexp = NAO_EM_BRANCO, message = "Timezone não pode ser vazio")
    @field:TimezoneSuportado
    val timezone: String? = null,

    @field:Min(value = 30, message = "Duração de preparo deve ter no mínimo 30s")
    @field:Max(value = 900, message = "Duração de preparo deve ter no máximo 900s")
    val duracaoPreparoS: Int? = null,

    @field:Positive(message = "Limiar de descalcificação deve ser maior que zero")
    val limiarDescalcificacao: Int? = null
)

data class CompartilharDispositivoRequest(
    @field:NotBlank(message = "Email do convidado é obrigatório")
    @field:Email(message = "Email inválido")
    val email: String
)

data class DispositivoResponse(
    val id: UUID,
    val nome: String,
    val timezone: String,
    val estado: String,
    val online: Boolean,
    val ultimoVisto: Instant?,
    val versaoAgendamentos: Int,
    val duracaoPreparoS: Int,
    val limiarDescalcificacao: Int,
    val contadorPreparos: Int,
    val papel: PapelDispositivo,
    val criadoEm: Instant,
    val atualizadoEm: Instant
)

data class CompartilhamentoResponse(
    val usuarioId: UUID,
    val nome: String,
    val email: String,
    val papel: PapelDispositivo,
    val compartilhadoEm: Instant
)
