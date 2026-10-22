package br.com.tavaressan.cafey.shared.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalCafeyColors = staticCompositionLocalOf { CafeyLightColors }
private val LocalCafeyTypography = staticCompositionLocalOf<CafeyTypography?> { null }
private val LocalCafeyShapes = staticCompositionLocalOf { CafeyDefaultShapes }

/** Acesso à paleta corrente do tema Caféy: `CafeyTheme.colors.brand`, etc. */
object CafeyTheme {
    val colors: CafeyColors
        @Composable get() = LocalCafeyColors.current

    val typography: CafeyTypography
        @Composable get() = requireNotNull(LocalCafeyTypography.current) {
            "CafeyTheme.typography só está disponível dentro de um bloco CafeyTheme { ... }"
        }

    val shapes: CafeyShapes
        @Composable get() = LocalCafeyShapes.current
}

/**
 * Raiz do design system Caféy — traduz `docs/docs_interface/prototype/assets/cafey.css` para
 * Compose. Envolva o topo de cada app (`MainActivity`, `main()` do desktop, `main()` do web) com
 * este composable para que `CafeyTheme.colors` / `.typography` / `.shapes` fiquem disponíveis.
 *
 * @param darkTheme espelha `.theme-dark` do CSS — repassado pela plataforma (preferência do SO)
 * ou controlado manualmente pela tela "Base" (era um objetivo do protótipo).
 */
@Composable
fun CafeyTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) CafeyDarkColors else CafeyLightColors
    val typography = cafeyTypography()

    CompositionLocalProvider(
        LocalCafeyColors provides colors,
        LocalCafeyTypography provides typography,
        LocalCafeyShapes provides CafeyDefaultShapes,
    ) {
        // Os componentes Material 3 (campos de texto, diálogos, botões sem cor própria) leem o
        // colorScheme; sem ele caem no esquema claro padrão e o texto digitado fica quase preto
        // sobre o fundo escuro.
        MaterialTheme(colorScheme = materialColorScheme(colors, darkTheme), content = content)
    }
}

/** Projeta os tokens Caféy nos papéis do Material 3 que as telas usam sem cor explícita. */
private fun materialColorScheme(colors: CafeyColors, darkTheme: Boolean): ColorScheme {
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    return base.copy(
        // No escuro, o brand (#A33A21) como texto daria ~2,7:1 sobre o ground; usa o brandDeep,
        // que o tema escuro clareia para texto.
        primary = if (darkTheme) colors.brandDeep else colors.brand,
        onPrimary = colors.brandOn,
        background = colors.ground,
        onBackground = colors.ink,
        surface = colors.surface,
        onSurface = colors.ink,
        onSurfaceVariant = colors.ink3,
        surfaceContainerHigh = colors.surface,
    )
}
