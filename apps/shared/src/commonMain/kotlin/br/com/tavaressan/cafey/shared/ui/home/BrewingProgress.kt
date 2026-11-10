package br.com.tavaressan.cafey.shared.ui.home

import br.com.tavaressan.cafey.shared.domain.model.DeviceState
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Progresso do anel exibido durante o preparo (issue #300).
 *
 * A fonte da verdade é o estado recebido da cafeteira (`PREPARANDO`) e o horário de início
 * reportado por ela (`preparoIniciadoEm` / `desde`) somado à duração programada. A UI pode
 * interpolar a animação entre pollings a partir desses dados, mas não deve usar um cronômetro
 * local iniciado no clique.
 */
sealed interface BrewingProgress {
    /** Sem preparo em andamento (ocioso, pronto, erro, cancelado): anel recolhido. */
    data object Inactive : BrewingProgress

    /** Preparo em andamento sem horário de início conhecido: anel indeterminado. */
    data object Indeterminate : BrewingProgress

    /** Preparo em andamento com progresso conhecido, de 0f a 1f. */
    data class Determinate(val fraction: Float) : BrewingProgress
}

@OptIn(ExperimentalTime::class)
fun calcularProgressoPreparo(
    deviceState: DeviceState,
    preparoIniciadoEm: String?,
    duracaoPreparoS: Int,
    nowEpochMs: Long,
): BrewingProgress {
    if (deviceState != DeviceState.Brewing) return BrewingProgress.Inactive
    if (preparoIniciadoEm.isNullOrBlank() || duracaoPreparoS <= 0) return BrewingProgress.Indeterminate
    val inicioMs = try {
        Instant.parse(preparoIniciadoEm).toEpochMilliseconds()
    } catch (_: Exception) {
        return BrewingProgress.Indeterminate
    }
    val elapsedMs = (nowEpochMs - inicioMs).coerceAtLeast(0L)
    val fraction = (elapsedMs.toFloat() / (duracaoPreparoS * 1000f)).coerceIn(0f, 1f)
    return BrewingProgress.Determinate(fraction)
}
