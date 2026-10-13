package br.com.tavaressan.cafey.shared.ui.schedule

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Issue #189 — seletor segmentado "Desliga sozinha após" (`.seg` de cafey.css): 4/6/8/10 min, seleção
 * única, mapeando cada opção para os segundos enviados ao backend (240/360/480/600).
 */
@OptIn(ExperimentalTestApi::class)
class DurationSegmentedControlTest {

    @Test
    fun showsFourOptionsAndMarksTheSelectedOne() = runComposeUiTest {
        setContent { CafeyTheme { DurationSegmentedControl(selected = 480, onSelect = {}) } }

        onNodeWithText("4 min").assertIsNotSelected()
        onNodeWithText("6 min").assertIsNotSelected()
        onNodeWithText("8 min").assertIsSelected()
        onNodeWithText("10 min").assertIsNotSelected()
    }

    @Test
    fun marksNoOptionWhenSelectionIsNull() = runComposeUiTest {
        setContent { CafeyTheme { DurationSegmentedControl(selected = null, onSelect = {}) } }

        listOf("4 min", "6 min", "8 min", "10 min").forEach { onNodeWithText(it).assertIsNotSelected() }
    }

    @Test
    fun clickingAnOptionReportsItsDurationInSeconds() = runComposeUiTest {
        var picked: Int? = null
        setContent { CafeyTheme { DurationSegmentedControl(selected = 480, onSelect = { picked = it }) } }

        onNodeWithText("6 min").performClick()
        assertEquals(360, picked)

        onNodeWithText("10 min").performClick()
        assertEquals(600, picked)
    }
}
