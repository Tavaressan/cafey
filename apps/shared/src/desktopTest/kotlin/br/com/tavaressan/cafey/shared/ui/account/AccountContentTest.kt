package br.com.tavaressan.cafey.shared.ui.account

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/** Issue #183 — clicar em "Sair" deve disparar [AccountContent]'s `onLogout`. */
@OptIn(ExperimentalTestApi::class)
class AccountContentTest {

    @Test
    fun clickingSairInvokesOnLogout() = runComposeUiTest {
        var loggedOut = false
        setContent {
            CafeyTheme {
                AccountContent(onLogout = { loggedOut = true })
            }
        }

        onNodeWithText("Sair").performClick()

        assertTrue(loggedOut, "onLogout deveria ter sido chamado ao clicar em Sair")
    }
}
