package br.com.tavaressan.cafey.shared.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Faixa de largura da casca de navegação — espelha os 3 breakpoints de `cafey.css`
 * (`docs/docs_interface/prototype/assets/cafey.css`): < 768 = barra de abas, 768–1023 = trilho de
 * ícones, ≥ 1024 = sidebar.
 */
enum class NavShellSizeClass {
    /** < 768.dp — barra de abas no rodapé, coluna única. */
    Compact,

    /** 768.dp..1023.dp — trilho de ícones lateral (tablet). */
    Medium,

    /** ≥ 1024.dp — sidebar lateral com rótulos (desktop). */
    Expanded,
}

internal val MEDIUM_BREAKPOINT: Dp = 768.dp
internal val EXPANDED_BREAKPOINT: Dp = 1024.dp

/** Deriva a faixa de navegação a partir da largura disponível, em dp. */
fun navShellSizeClassFor(width: Dp): NavShellSizeClass = when {
    width < MEDIUM_BREAKPOINT -> NavShellSizeClass.Compact
    width < EXPANDED_BREAKPOINT -> NavShellSizeClass.Medium
    else -> NavShellSizeClass.Expanded
}

/**
 * Largura máxima do conteúdo (`.shell` do protótipo) para cada faixa. Em [NavShellSizeClass.Compact]
 * usa o limite específico da plataforma ([compactMax]) — 390.dp em Desktop/Web, sem limite no
 * Android; nas faixas maiores usa os valores fixos do design (754px no tablet, 1018px no desktop).
 */
fun contentMaxWidthFor(sizeClass: NavShellSizeClass, compactMax: Dp): Dp = when (sizeClass) {
    NavShellSizeClass.Compact -> compactMax
    NavShellSizeClass.Medium -> 754.dp
    NavShellSizeClass.Expanded -> 1018.dp
}
