package br.com.tavaressan.cafey.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import br.com.tavaressan.cafey.shared.ui.auth.LoginScreen
import br.com.tavaressan.cafey.shared.ui.auth.RegisterScreen
import br.com.tavaressan.cafey.shared.ui.history.HistoryScreen
import br.com.tavaressan.cafey.shared.ui.home.HomeScreen
import br.com.tavaressan.cafey.shared.ui.schedule.ScheduleScreen
import br.com.tavaressan.cafey.shared.LocalAppContainer
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme

private const val ROUTE_LOGIN = "login"
private const val ROUTE_REGISTER = "register"
private const val ROUTE_HOME = "home"
private const val ROUTE_SCHEDULE = "schedule"
private const val ROUTE_HISTORY = "history"

/** Abas do rodapé principal — espelha `assets/nav.js` do protótipo. "Cuidados" e "Base" entram
 * conforme as telas correspondentes forem implementadas (APP-07); "Base" (detalhes de hardware)
 * não tem issue própria ainda. */
private data class BottomTab(val route: String, val label: String)

private val BOTTOM_TABS = listOf(
    BottomTab(ROUTE_HOME, "Início"),
    BottomTab(ROUTE_SCHEDULE, "Agenda"),
    BottomTab(ROUTE_HISTORY, "Ritmo"),
)

/**
 * Navegação entre login, cadastro e as telas principais do app (APP-03 a APP-06). Antes de decidir
 * a tela inicial, checa se já existe uma sessão válida (token persistido) — "ao reabrir o app com
 * token válido, entra direto".
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
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.hierarchy?.firstOrNull { dest ->
        BOTTOM_TABS.any { it.route == dest.route }
    }?.route

    Scaffold(
        containerColor = CafeyTheme.colors.ground,
        bottomBar = {
            if (currentRoute != null) {
                CafeyBottomBar(currentRoute = currentRoute, onSelect = { route -> navigateToTab(navController, route) })
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier.padding(padding),
        ) {
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
            composable(ROUTE_HOME) { HomeScreen() }
            composable(ROUTE_SCHEDULE) { ScheduleScreen() }
            composable(ROUTE_HISTORY) { HistoryScreen() }
        }
    }
}

private fun navigateToTab(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun CafeyBottomBar(currentRoute: String, onSelect: (String) -> Unit) {
    NavigationBar(containerColor = CafeyTheme.colors.surface) {
        BOTTOM_TABS.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = { onSelect(tab.route) },
                icon = {},
                label = { Text(tab.label, style = CafeyTheme.typography.caption) },
                colors = NavigationBarItemDefaults.colors(
                    selectedTextColor = CafeyTheme.colors.brand,
                    unselectedTextColor = CafeyTheme.colors.muted,
                    indicatorColor = CafeyTheme.colors.brandTint,
                ),
            )
        }
    }
}
