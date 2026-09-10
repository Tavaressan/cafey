package br.com.tavaressan.cafey.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import br.com.tavaressan.cafey.shared.ui.auth.LoginScreen
import br.com.tavaressan.cafey.shared.ui.auth.RegisterScreen
import br.com.tavaressan.cafey.shared.ui.home.HomeScreen
import br.com.tavaressan.cafey.shared.LocalAppContainer
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme

private const val ROUTE_LOGIN = "login"
private const val ROUTE_REGISTER = "register"
private const val ROUTE_HOME = "home"

/**
 * Navegação entre login, cadastro e início (APP-03). Antes de decidir a tela inicial, checa se já
 * existe uma sessão válida (token persistido) — "ao reabrir o app com token válido, entra direto".
 */
@Composable
fun CafeyNavHost() {
    val container = LocalAppContainer.current
    var sessionChecked by remember { mutableStateOf(false) }
    var startRoute by remember { mutableStateOf(ROUTE_LOGIN) }

    LaunchedEffect(Unit) {
        startRoute = if (container.apiClient.isAuthenticated()) ROUTE_HOME else ROUTE_LOGIN
        sessionChecked = true
    }

    if (!sessionChecked) {
        Box(
            modifier = Modifier.fillMaxSize().background(CafeyTheme.colors.ground),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = CafeyTheme.colors.brand)
        }
        return
    }

    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = startRoute) {
        composable(ROUTE_LOGIN) {
            LoginScreen(
                onLoggedIn = { navController.navigate(ROUTE_HOME) { popUpTo(ROUTE_LOGIN) { inclusive = true } } },
                onGoToRegister = { navController.navigate(ROUTE_REGISTER) },
            )
        }
        composable(ROUTE_REGISTER) {
            RegisterScreen(
                onRegistered = { navController.navigate(ROUTE_HOME) { popUpTo(ROUTE_LOGIN) { inclusive = true } } },
                onGoToLogin = { navController.popBackStack() },
            )
        }
        composable(ROUTE_HOME) {
            HomeScreen()
        }
    }
}
