package br.com.tavaressan.cafey.event

/** Cuidados de manutenção da máquina exibidos na tela Cuidados, contados em preparos. */
enum class TipoCuidado { ENXAGUE, FILTRO, DESCALCIFICACAO }

/**
 * Issue #190 — regra de qual cuidado vira o destaque: o de maior fração contador/limiar.
 * A fração não tem teto (ao contrário de `percentualUso`), para que um cuidado muito estourado
 * ganhe de outro que apenas atingiu o limiar. Empate segue a ordem do protótipo (care.html).
 */
object DestaqueCuidado {
    private val ordem = listOf(TipoCuidado.ENXAGUE, TipoCuidado.FILTRO, TipoCuidado.DESCALCIFICACAO)

    fun fracao(contador: Int, limiar: Int): Double =
        if (limiar > 0) contador.toDouble() / limiar else 0.0

    // maxByOrNull devolve o primeiro elemento em caso de empate, por isso a ordem decide o desempate.
    fun escolher(fracoes: Map<TipoCuidado, Double>): TipoCuidado =
        ordem.maxByOrNull { fracoes.getValue(it) } ?: TipoCuidado.ENXAGUE
}
