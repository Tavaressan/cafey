package br.com.tavaressan.cafey.shared.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Marca oficial da Caféy — grão de café estilizado com as duas curvas em S internas
 * e a estrela central de quatro pontas.
 */
object CafeyBrandAssets {
    val BeanColor = Color(0xFFD2784D)
    val StarColor = Color(0xFF7092BE)

    val Logo: ImageVector by lazy {
        val outline = PathParser().parsePathString("M 256 86 C 322 86 372 162 372 252 C 372 348 320 426 256 426 C 192 426 140 348 140 252 C 140 162 190 86 256 86 Z").toNodes()
        val leftCurve = PathParser().parsePathString("M 256.0 104.0 C 252.1 107.6 236.3 118.5 232.5 125.7 C 228.7 133.0 232.7 140.2 233.0 147.4 C 233.2 154.7 234.0 161.9 233.8 169.1 C 233.7 176.4 233.4 183.6 232.3 190.9 C 231.2 198.1 229.5 205.3 227.3 212.6 C 225.2 219.8 222.3 227.0 219.4 234.3 C 216.5 241.5 213.0 248.8 210.0 256.0 C 207.0 263.2 203.7 270.5 201.3 277.7 C 198.9 285.0 196.7 292.2 195.5 299.4 C 194.3 306.7 193.8 313.9 194.3 321.1 C 194.7 328.4 196.1 335.6 198.3 342.9 C 200.5 350.1 203.6 357.3 207.4 364.6 C 211.2 371.8 212.8 379.0 220.9 386.3 C 229.0 393.5 250.1 404.4 256.0 408.0").toNodes()
        val rightCurve = PathParser().parsePathString("M 256.0 104.0 C 261.9 107.6 283.0 118.5 291.1 125.7 C 299.2 133.0 300.8 140.2 304.6 147.4 C 308.4 154.7 311.5 161.9 313.7 169.1 C 315.9 176.4 317.3 183.6 317.7 190.9 C 318.2 198.1 317.7 205.3 316.5 212.6 C 315.3 219.8 313.1 227.0 310.7 234.3 C 308.3 241.5 305.0 248.8 302.0 256.0 C 299.0 263.2 295.5 270.5 292.6 277.7 C 289.7 285.0 286.8 292.2 284.7 299.4 C 282.5 306.7 280.8 313.9 279.7 321.1 C 278.6 328.4 278.3 335.6 278.2 342.9 C 278.0 350.1 278.8 357.3 279.0 364.6 C 279.3 371.8 283.3 379.0 279.5 386.3 C 275.7 393.5 259.9 404.4 256.0 408.0").toNodes()
        val star = PathParser().parsePathString("M 256.0 226.0 Q 263.0 249.0 286.0 256.0 Q 263.0 263.0 256.0 286.0 Q 249.0 263.0 226.0 256.0 Q 249.0 249.0 256.0 226.0 Z").toNodes()

        ImageVector.Builder(
            name = "CafeyLogo",
            defaultWidth = 512.dp,
            defaultHeight = 512.dp,
            viewportWidth = 512f,
            viewportHeight = 512f,
        ).addPath(
            pathData = outline,
            fill = null,
            stroke = SolidColor(BeanColor),
            strokeLineWidth = 16f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).addPath(
            pathData = leftCurve,
            fill = null,
            stroke = SolidColor(BeanColor),
            strokeLineWidth = 13f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).addPath(
            pathData = rightCurve,
            fill = null,
            stroke = SolidColor(BeanColor),
            strokeLineWidth = 13f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).addPath(
            pathData = star,
            fill = SolidColor(StarColor),
            stroke = null,
        ).build()
    }
}

@Composable
fun CafeyLogo(modifier: Modifier = Modifier, size: Dp = 48.dp) {
    Image(
        imageVector = CafeyBrandAssets.Logo,
        contentDescription = "Caféy Logo",
        modifier = modifier.size(size),
    )
}

@Composable
fun CafeyWordmark(modifier: Modifier = Modifier, iconSize: Dp = 36.dp) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CafeyLogo(size = iconSize)
        Text(
            text = "Caféy",
            style = CafeyTheme.typography.screenTitle,
            color = CafeyTheme.colors.ink,
        )
    }
}
