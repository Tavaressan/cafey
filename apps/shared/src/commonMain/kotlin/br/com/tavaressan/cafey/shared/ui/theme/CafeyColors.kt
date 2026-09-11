package br.com.tavaressan.cafey.shared.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta de cores do design system Caféy.
 *
 * Espelha os tokens de `docs/docs_interface/prototype/assets/cafey.css` (`:root` e `.theme-dark`).
 * Não é um `MaterialTheme.colorScheme` — os componentes do protótipo usam nomes próprios
 * (brand, blue, ground, ink...) em vez do vocabulário do Material 3.
 */
data class CafeyColors(
    // Marca
    val brand: Color,
    val brandHover: Color,
    val brandDeep: Color,
    val brandTint: Color,
    val brandInk: Color,
    val brandOn: Color,

    // Estrela / estado conectado
    val blue: Color,
    val blueDeep: Color,
    val blueTint: Color,
    val blueInk: Color,
    val blueSoft: Color,
    val blueOff: Color,

    // Superfícies
    val ground: Color,
    val surface: Color,
    val sunken: Color,
    val line: Color,
    val line2: Color,
    val line3: Color,

    // Texto
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val ink4: Color,
    val muted: Color,
    val dim: Color,

    // Gráficos
    val chartHi: Color,
    val chartMid: Color,
    val chartLo: Color,
    val chartFlat: Color,
)

/** Tema claro — valores padrão de `:root` em `cafey.css`. */
val CafeyLightColors = CafeyColors(
    brand = Color(0xFFA33A21),
    brandHover = Color(0xFF8C3018),
    brandDeep = Color(0xFF7E2B18),
    brandTint = Color(0xFFF6E1D9),
    brandInk = Color(0xFF96513C),
    brandOn = Color(0xFFFEF3EE),

    blue = Color(0xFF6E8FBC),
    blueDeep = Color(0xFF4A6A93),
    blueTint = Color(0xFFEDF1F7),
    blueInk = Color(0xFF3F587A),
    blueSoft = Color(0xFF8FA3BC),
    blueOff = Color(0xFFD5DCE7),

    ground = Color(0xFFFBF8F4),
    surface = Color(0xFFFFFFFF),
    sunken = Color(0xFFF3EEE7),
    line = Color(0xFFEDE5DA),
    line2 = Color(0xFFE7DFD4),
    line3 = Color(0xFFE1D8CD),

    ink = Color(0xFF1B1D1F),
    ink2 = Color(0xFF2E3338),
    ink3 = Color(0xFF4C5257),
    ink4 = Color(0xFF5C6368),
    muted = Color(0xFF7C848B),
    dim = Color(0xFFB3A99F),

    chartHi = Color(0xFFA33A21),
    chartMid = Color(0xFFD9C4BA),
    chartLo = Color(0xFFE9E1D8),
    chartFlat = Color(0xFFE4DAD3),
)

/** Tema escuro — bloco `.theme-dark` em `cafey.css`. Sobrescreve só o que o CSS sobrescreve;
 * o resto (marca, azul, gráficos) herda do claro, como no protótipo. */
val CafeyDarkColors = CafeyLightColors.copy(
    ground = Color(0xFF17191B),
    surface = Color(0xFF1F2225),
    sunken = Color(0xFF23272A),
    line = Color(0xFF2C3033),
    line2 = Color(0xFF2A2E31),
    line3 = Color(0xFF363B40),

    ink = Color(0xFFF1EDE9),
    ink2 = Color(0xFFE6E1DC),
    ink3 = Color(0xFFD7D2CD),
    ink4 = Color(0xFFC7C2BD),
    muted = Color(0xFF8B9298),

    blueSoft = Color(0xFF9FB4CF),
)
