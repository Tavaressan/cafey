package br.com.tavaressan.cafey.shared.ui.schedule

import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regressão das issues #188 e #201:
 * - Em Desktop (`isExpanded = true`), [list] e [editor] devem ficar lado a lado (grid `.wide--late`
 *   1fr/1fr de cafey.css).
 * - Abaixo disso, se [showEditorInCompact] for falso, exibe apenas [list]; se verdadeiro, exibe [editor].
 * - Crucialmente (issue #201): ao alternar [isExpanded] com o editor aberto, o slot [editor] NÃO é
 *   descartado nem recriado, preservando foco e estado de IME.
 */
@OptIn(ExperimentalTestApi::class)
class ScheduleContentLayoutTest {

    @Test
    fun showsOnlyListWhenNotExpanded() = runComposeUiTest {
        setContent {
            CafeyTheme {
                ScheduleContentLayout(
                    isExpanded = false,
                    list = { Text("list", modifier = Modifier.testTag("list")) },
                    editor = { Text("editor", modifier = Modifier.testTag("editor")) },
                )
            }
        }

        onNodeWithTag("list").assertExists()
        onNodeWithText("editor").assertDoesNotExist()
    }

    @Test
    fun showsOnlyEditorWhenNotExpandedAndShowEditorInCompactIsTrue() = runComposeUiTest {
        setContent {
            CafeyTheme {
                ScheduleContentLayout(
                    isExpanded = false,
                    showEditorInCompact = true,
                    list = { Text("list", modifier = Modifier.testTag("list")) },
                    editor = { Text("editor", modifier = Modifier.testTag("editor")) },
                )
            }
        }

        onNodeWithTag("list").assertDoesNotExist()
        onNodeWithTag("editor").assertExists()
    }

    @Test
    fun placesListAndEditorSideBySideWhenExpanded() = runComposeUiTest {
        setContent {
            CafeyTheme {
                ScheduleContentLayout(
                    isExpanded = true,
                    list = { Text("list", modifier = Modifier.testTag("list")) },
                    editor = { Text("editor", modifier = Modifier.testTag("editor")) },
                )
            }
        }

        val listBounds = onNodeWithTag("list").getUnclippedBoundsInRoot()
        val editorBounds = onNodeWithTag("editor").getUnclippedBoundsInRoot()

        assertEquals(listBounds.top, editorBounds.top, "deveriam estar na mesma linha (lado a lado)")
        assertTrue(editorBounds.left > listBounds.left, "editor deveria vir a direita da lista")
    }

    @Test
    fun editorIsNotDisposedWhenCrossingBreakpointWithEditorOpen() = runComposeUiTest {
        var disposals = 0
        var isExpanded by mutableStateOf(false)

        setContent {
            CafeyTheme {
                ScheduleContentLayout(
                    isExpanded = isExpanded,
                    showEditorInCompact = true,
                    list = { Text("list") },
                    editor = {
                        DisposableEffect(Unit) { onDispose { disposals++ } }
                        Text("editor")
                    },
                )
            }
        }

        waitForIdle()
        assertEquals(0, disposals, "não deveria ter descartado o editor na composição inicial")

        isExpanded = true
        waitForIdle()
        assertEquals(0, disposals, "editor NÃO deve ser descartado ao cruzar breakpoint para Expanded")

        isExpanded = false
        waitForIdle()
        assertEquals(0, disposals, "editor NÃO deve ser descartado ao voltar para Compact")
    }
}
