package br.com.tavaressan.cafey.shared.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * O "sparkle" de quatro pontas que aparece em todo o protótipo (`.star` em `cafey.css`) — mesmo
 * símbolo do LED da base. Path normalizado a partir do original do protótipo
 * (`M 256 226 Q 263 249 286 256 Q 263 263 256 286 Q 249 263 226 256 Q 249 249 256 226 Z`,
 * viewBox 220–292), reescalado para caber em qualquer tamanho.
 */
@Composable
fun CafeyStar(color: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier = modifier.size(size)) {
        val s = size.toPx()
        fun pt(x: Double, y: Double) = Offset((x * s).toFloat(), (y * s).toFloat())

        val top = pt(0.5, 0.0)
        val right = pt(1.0, 0.5)
        val bottom = pt(0.5, 1.0)
        val left = pt(0.0, 0.5)
        val c1 = pt(0.6167, 0.3833)
        val c2 = pt(0.6167, 0.6167)
        val c3 = pt(0.3833, 0.6167)
        val c4 = pt(0.3833, 0.3833)

        val path = Path().apply {
            moveTo(top.x, top.y)
            quadraticBezierTo(c1.x, c1.y, right.x, right.y)
            quadraticBezierTo(c2.x, c2.y, bottom.x, bottom.y)
            quadraticBezierTo(c3.x, c3.y, left.x, left.y)
            quadraticBezierTo(c4.x, c4.y, top.x, top.y)
            close()
        }
        drawPath(path, color = color)
    }
}
