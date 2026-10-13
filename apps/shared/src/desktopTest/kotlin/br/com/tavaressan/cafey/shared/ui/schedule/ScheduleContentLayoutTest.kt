package br.com.tavaressan.cafey.shared.ui.schedule

import androidx.compose.material3.Text
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
 * Regressão da issue #188: em Desktop (`isExpanded = true`), [list] e [editor] devem ficar lado a
 * lado (grid `.wide--late` 1fr/1fr de cafey.css), em vez do editor substituir a lista na tela
 * toda; abaixo disso, só [list] é exibida (o editor em tela cheia é responsabilidade do caller).
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
}
