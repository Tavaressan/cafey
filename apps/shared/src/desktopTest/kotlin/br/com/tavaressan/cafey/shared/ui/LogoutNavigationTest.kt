package br.com.tavaressan.cafey.shared.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Issue #183 — reproduz, com o `NavHostController` real, exatamente a navegação de logout usada em
 * `CafeyNavHost` (`onLoggedOut`). Confirma que `popUpTo(navController.graph.id) { inclusive = true }`
 * — e não `popUpTo(ROUTE_LOGIN) { inclusive = true }`, o padrão citado na issue — é o que realmente
 * limpa a back stack inteira: depois de login→home, "login" já não está mais na pilha, então um
 * `popUpTo` sobre ele não descartaria nada.
 */
@OptIn(ExperimentalTestApi::class)
class LogoutNavigationTest {

    @Test
    fun logoutClearsBackStackAndNavigatesToLogin() = runComposeUiTest {
        lateinit var navController: NavHostController

        setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = "login") {
                composable("login") { Text("login") }
                composable("home") { Text("home") }
                composable("account") { Text("account") }
            }
        }

        waitForIdle()
        navController.navigate("home") { popUpTo("login") { inclusive = true } }
        waitForIdle()
        navController.navigate("account")
        waitForIdle()

        navController.navigate("login") { popUpTo(navController.graph.id) { inclusive = true } }
        waitForIdle()

        assertEquals("login", navController.currentBackStackEntry?.destination?.route)
        assertFalse(navController.popBackStack(), "nao deveria sobrar nada na back stack apos o logout")
    }
}
