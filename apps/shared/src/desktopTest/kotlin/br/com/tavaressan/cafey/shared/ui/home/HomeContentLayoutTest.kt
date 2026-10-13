package br.com.tavaressan.cafey.shared.ui.home

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import br.com.tavaressan.cafey.shared.ui.NavShellSizeClass
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regressão da issue #188: em Desktop (`NavShellSizeClass.Expanded`), o mostrador+ação
 * ([stage]) e o próximo preparo+sequência de manhãs ([sidebar]) devem ficar lado a lado
 * (grid `.wide--home` 1.22fr/.78fr de cafey.css); abaixo disso, empilhados numa coluna só.
 */
@OptIn(ExperimentalTestApi::class)
class HomeContentLayoutTest {

    @Test
    fun stacksStageAboveSidebarWhenNotExpanded() = runComposeUiTest {
        setContent {
            CafeyTheme {
                HomeContentLayout(
                    sizeClass = NavShellSizeClass.Medium,
                    stage = { Text("stage", modifier = Modifier.testTag("stage")) },
                    sidebar = { Text("sidebar", modifier = Modifier.testTag("sidebar")) },
                )
            }
        }

        val stageBounds = onNodeWithTag("stage").getUnclippedBoundsInRoot()
        val sidebarBounds = onNodeWithTag("sidebar").getUnclippedBoundsInRoot()

        assertEquals(stageBounds.left, sidebarBounds.left, "deveriam estar na mesma coluna (empilhados)")
        assertTrue(sidebarBounds.top > stageBounds.top, "sidebar deveria vir abaixo do stage")
    }

    @Test
    fun placesStageAndSidebarSideBySideWhenExpanded() = runComposeUiTest {
        setContent {
            CafeyTheme {
                HomeContentLayout(
                    sizeClass = NavShellSizeClass.Expanded,
                    stage = { Text("stage", modifier = Modifier.testTag("stage")) },
                    sidebar = { Text("sidebar", modifier = Modifier.testTag("sidebar")) },
                )
            }
        }

        val stageBounds = onNodeWithTag("stage").getUnclippedBoundsInRoot()
        val sidebarBounds = onNodeWithTag("sidebar").getUnclippedBoundsInRoot()

        assertEquals(stageBounds.top, sidebarBounds.top, "deveriam estar na mesma linha (lado a lado)")
        assertTrue(sidebarBounds.left > stageBounds.left, "sidebar deveria vir a direita do stage")
    }
}
