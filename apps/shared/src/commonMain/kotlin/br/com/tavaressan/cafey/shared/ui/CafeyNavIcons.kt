package br.com.tavaressan.cafey.shared.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Ícones de traço (21×21, viewBox 24×24, stroke-width 1.7) das abas de navegação — mesmos paths
 * SVG usados no protótipo HTML (`docs/docs_interface/prototype/assets/nav.js`, array `ITEMS`),
 * convertidos para `ImageVector`. `fill = null` porque no CSS os ícones são só traço
 * (`.tabbar__item svg { fill: none }`); a cor é aplicada via `tint` em `Icon(...)`.
 */
internal object CafeyNavIcons {
    val Home: ImageVector = navIcon("nav_home", "M4 11l8-6 8 6v8a1 1 0 0 1-1 1h-4v-6H9v6H5a1 1 0 0 1-1-1z")

    val Schedule: ImageVector = navIcon(
        "nav_schedule",
        "M4 6 h16 a2.5 2.5 0 0 1 2.5 2.5 v11.5 a2.5 2.5 0 0 1 -2.5 2.5 h-16 a2.5 2.5 0 0 1 -2.5 -2.5 v-11.5 a2.5 2.5 0 0 1 2.5 -2.5 z" +
            "M8 3.5v4M16 3.5v4M4 11h16",
    )

    val Rhythm: ImageVector = navIcon("nav_rhythm", "M4 19.5h16M5 15l4-5 3.5 3L18 6")

    val Care: ImageVector = navIcon(
        "nav_care",
        "M12 3.5s5.8 6.3 5.8 10.2A5.8 5.8 0 0 1 6.2 13.7C6.2 9.8 12 3.5 12 3.5z",
    )

    // Issue #182 — `<rect x="7.5" y="7.5" width="9" height="9" rx="2">` do protótipo convertido para
    // path equivalente (PathParser não lê `<rect>`), combinado com o "+" de conector nos 4 lados.
    val Base: ImageVector = navIcon(
        "nav_base",
        "M9.5,7.5 H14.5 A2,2 0 0 1 16.5,9.5 V14.5 A2,2 0 0 1 14.5,16.5 H9.5 A2,2 0 0 1 7.5,14.5 V9.5 A2,2 0 0 1 9.5,7.5 Z" +
            "M10 4.5v3M14 4.5v3M10 16.5v3M14 16.5v3M4.5 10h3M4.5 14h3M16.5 10h3M16.5 14h3",
    )

    private fun navIcon(name: String, svgPathData: String): ImageVector {
        val nodes = PathParser().parsePathString(svgPathData).toNodes()
        return ImageVector.Builder(
            name = name,
            defaultWidth = 21.dp,
            defaultHeight = 21.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = nodes,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
    }
}
