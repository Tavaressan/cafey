package br.com.tavaressan.cafey.shared.ui.theme

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
        content = content,
    )
}
