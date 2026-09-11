package br.com.tavaressan.cafey.shared.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Raios de borda do design system Caféy — variáveis `--r-*` de `cafey.css`. */
data class CafeyShapes(
    val extraLarge: RoundedCornerShape = RoundedCornerShape(22.dp),
    val large: RoundedCornerShape = RoundedCornerShape(20.dp),
    val medium: RoundedCornerShape = RoundedCornerShape(17.dp),
    val small: RoundedCornerShape = RoundedCornerShape(15.dp),
)

val CafeyDefaultShapes = CafeyShapes()
