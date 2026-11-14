package br.com.tavaressan.cafey.shared.ui.home

import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Textos do tempo restante do preparo (issue #376). Funcoes puras, sem Compose, para serem
 * testadas e reaproveitadas (o OLED, se vier, usa a mesma regra).
 */

/** Abaixo disto o texto deixa de contar segundos e vira "menos de 1 min". */
internal const val LIMITE_MENOS_DE_UM_MINUTO_S = 60L

/** "4:32" (m:ss), "1:02:03" (h:mm:ss) ou "menos de 1 min". [estimado] acrescenta "~". */
internal fun textoTempoRestante(restanteS: Long, estimado: Boolean = false): String {
    val base = when {
        restanteS <= 0L -> "agora"
        restanteS < LIMITE_MENOS_DE_UM_MINUTO_S -> "menos de 1 min"
        else -> {
            val h = restanteS / 3600
            val m = (restanteS % 3600) / 60
            val s = restanteS % 60
            if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
            else "$m:${s.toString().padStart(2, '0')}"
        }
    }
    return if (estimado && restanteS >= LIMITE_MENOS_DE_UM_MINUTO_S) "~$base" else base
}

/** "07:42" no fuso informado. */
@OptIn(ExperimentalTime::class)
internal fun horaPrevista(terminaEmMs: Long, fuso: TimeZone): String {
    val quando = Instant.fromEpochMilliseconds(terminaEmMs).toLocalDateTime(fuso)
    return "${quando.hour.toString().padStart(2, '0')}:${quando.minute.toString().padStart(2, '0')}"
}

/** "pronto às 07:42" no fuso informado. */
internal fun textoHorarioPrevisto(terminaEmMs: Long, fuso: TimeZone): String =
    "pronto às ${horaPrevista(terminaEmMs, fuso)}"

/**
 * Descricao para leitor de tela. Arredonda para o multiplo de 30 s mais proximo (para cima)
 * para o texto so mudar a cada 30 s e o leitor nao falar a cada segundo.
 */
internal fun descricaoRestanteAcessivel(restanteS: Long): String {
    if (restanteS <= 0L) return "o cafe esta ficando pronto"
    if (restanteS < LIMITE_MENOS_DE_UM_MINUTO_S) return "falta menos de um minuto"
    val arredondado = ((restanteS + 29L) / 30L) * 30L
    val m = arredondado / 60
    val s = arredondado % 60
    val minutos = if (m == 1L) "1 minuto" else "$m minutos"
    return if (s == 0L) "faltam $minutos" else "faltam $minutos e $s segundos"
}
