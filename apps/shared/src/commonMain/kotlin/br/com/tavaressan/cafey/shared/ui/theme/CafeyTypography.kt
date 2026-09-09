package br.com.tavaressan.cafey.shared.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import br.com.tavaressan.cafey.shared.generated.resources.Res
import br.com.tavaressan.cafey.shared.generated.resources.instrument_sans_variable
import br.com.tavaressan.cafey.shared.generated.resources.jetbrains_mono_variable
import br.com.tavaressan.cafey.shared.generated.resources.space_grotesk_variable

/**
 * Estilos de texto do design system Caféy, espelhando as famílias `--display`, `--text` e `--mono`
 * de `cafey.css`. As fontes são recursos empacotados (Space Grotesk, Instrument Sans, JetBrains
 * Mono, todas sob licença SIL OFL); o `FontFamily.Default` do sistema entra como `fallback` quando
 * o glyph pedido não existe na fonte variável.
 */
data class CafeyTypography(
    val screenTitle: TextStyle,
    val cardTitle: TextStyle,
    val cardHero: TextStyle,
    val sentence: TextStyle,
    val body: TextStyle,
    val bodySmall: TextStyle,
    val buttonLabel: TextStyle,
    val caption: TextStyle,
    val mono: TextStyle,
)

/**
 * Constrói a tipografia carregando as fontes empacotadas como recurso do Compose Multiplatform.
 * Precisa rodar em contexto `@Composable` porque `Font(resource, ...)` depende do carregador de
 * recursos da plataforma corrente.
 */
@Composable
fun cafeyTypography(): CafeyTypography {
    val display = FontFamily(
        Font(Res.font.space_grotesk_variable, weight = FontWeight.Normal),
        Font(Res.font.space_grotesk_variable, weight = FontWeight.Medium),
        Font(Res.font.space_grotesk_variable, weight = FontWeight.SemiBold),
        Font(Res.font.space_grotesk_variable, weight = FontWeight.Bold),
    )
    val text = FontFamily(
        Font(Res.font.instrument_sans_variable, weight = FontWeight.Normal),
        Font(Res.font.instrument_sans_variable, weight = FontWeight.Medium),
        Font(Res.font.instrument_sans_variable, weight = FontWeight.SemiBold),
        Font(Res.font.instrument_sans_variable, weight = FontWeight.Bold),
    )
    val mono = FontFamily(
        Font(Res.font.jetbrains_mono_variable, weight = FontWeight.Normal),
        Font(Res.font.jetbrains_mono_variable, weight = FontWeight.Medium),
    )

    return CafeyTypography(
        // .screen-title — 600 27px
        screenTitle = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 27.sp, letterSpacing = (-0.03).sp),
        // .card__title — 600 19px
        cardTitle = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, letterSpacing = (-0.02).sp),
        // .card__hero — 600 26px
        cardHero = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, letterSpacing = (-0.03).sp),
        // .sentence — 400 19px/1.4
        sentence = TextStyle(fontFamily = text, fontWeight = FontWeight.Normal, fontSize = 19.sp, lineHeight = 26.sp),
        // .card__body — 400 14px/1.55
        body = TextStyle(fontFamily = text, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
        // .screen-lede / .card__meta — 400-500 12.5-14px
        bodySmall = TextStyle(fontFamily = text, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, lineHeight = 18.sp),
        // .btn — 600 14.5px
        buttonLabel = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp),
        // .screen-foot / .tile__label — 500 11.5px
        caption = TextStyle(fontFamily = text, fontWeight = FontWeight.Medium, fontSize = 11.5.sp, lineHeight = 16.sp),
        // .stage__sub--mono — JetBrains Mono
        mono = TextStyle(fontFamily = mono, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    )
}
