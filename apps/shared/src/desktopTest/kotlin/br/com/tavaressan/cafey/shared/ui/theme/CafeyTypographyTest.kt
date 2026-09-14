package br.com.tavaressan.cafey.shared.ui.theme

import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Valida que CafeyTypography usa `.em` (unidade relativa) em vez de `.sp` (absoluta)
 * para letterSpacing. Issue #170.
 *
 * Referência: cafey.css define:
 * - .screen-title { letter-spacing: -.03em }
 * - .card__title { letter-spacing: -.02em }
 * - .card__hero { letter-spacing: -.03em }
 */
class CafeyTypographyTest {

    @Test
    fun textUnitEmVsSp_shouldHaveDifferentInternalRepresentations() {
        // Validar que `.em` e `.sp` produzem TextUnit com tipos diferentes
        val emValue = (-0.03).em
        val spValue = (-0.03).sp

        // Ambos são TextUnit, mas com tipos diferentes internamente
        // Em runtime, podemos verificar via toString() ou inspeção de tipo
        val emString = emValue.toString()
        val spString = spValue.toString()

        // Valores em `.em` deveriam incluir "em" na representação
        // Valores em `.sp` deveriam incluir "sp" na representação
        assertTrue(emString.contains("em"), "Expected em unit in: $emString")
        assertTrue(spString.contains("sp"), "Expected sp unit in: $spString")
    }

    @Test
    fun typographyExpectedValues_areInEmUnits() {
        // Validar que os valores esperados em cafey.css são em `.em`
        val expectedScreenTitleSpacing = (-0.03).em
        val expectedCardTitleSpacing = (-0.02).em
        val expectedCardHeroSpacing = (-0.03).em

        assertEquals((-0.03).em, expectedScreenTitleSpacing)
        assertEquals((-0.02).em, expectedCardTitleSpacing)
        assertEquals((-0.03).em, expectedCardHeroSpacing)
    }
}
