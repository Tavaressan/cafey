package br.com.tavaressan.cafey.shared.ui.base

import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

/**
 * Texto da "última comunicação" do módulo em linguagem natural ("há 2 min", "hoje às 14:32",
 * "ontem às 09:05"), em vez do timestamp ISO cru. Devolve [semDado] quando não há valor ou ele
 * não é uma data válida.
 */
@OptIn(ExperimentalTime::class)
internal fun textoUltimaComunicacao(
    iso: String?,
    agoraMs: Long,
    fuso: TimeZone,
    semDado: String = "Indisponível",
): String {
    val instante = try {
        Instant.parse(iso ?: return semDado)
    } catch (_: Exception) {
        return semDado
    }
    val diffMin = (agoraMs - instante.toEpochMilliseconds()) / 60_000
    if (diffMin < 1) return "agora mesmo" // inclui relógio do aparelho ligeiramente atrasado
    if (diffMin < 60) return "há $diffMin min"

    val quando = instante.toLocalDateTime(fuso)
    val hoje = Instant.fromEpochMilliseconds(agoraMs).toLocalDateTime(fuso).date
    val hora = "${quando.hour.toString().padStart(2, '0')}:${quando.minute.toString().padStart(2, '0')}"
    return when (quando.date) {
        hoje -> "hoje às $hora"
        hoje.minus(DatePeriod(days = 1)) -> "ontem às $hora"
        else -> {
            val dia = quando.day.toString().padStart(2, '0')
            val mes = (quando.month.ordinal + 1).toString().padStart(2, '0')
            "$dia/$mes às $hora"
        }
    }
}

internal fun textoEstadoRele(codigo: String?): String = when (codigo?.trim()?.uppercase()) {
    "LIGADO" -> "Ligado"
    "DESLIGADO" -> "Desligado"
    null, "" -> "Indisponível"
    else -> codigo
}

/** "24,5 °C" (vírgula decimal, uma casa), ou [semDado] quando o sensor não reportou. */
internal fun textoTemperatura(temperaturaC: Double?, semDado: String = "Indisponível"): String {
    if (temperaturaC == null) return semDado
    val decimos = (kotlin.math.abs(temperaturaC) * 10).roundToInt()
    val sinal = if (temperaturaC < 0 && decimos != 0) "-" else ""
    return "$sinal${decimos / 10},${decimos % 10} °C"
}

internal fun textoUmidade(umidade: Int?, semDado: String = "Indisponível"): String =
    umidade?.let { "$it %" } ?: semDado
