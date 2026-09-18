package br.com.tavaressan.cafey.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import br.com.tavaressan.cafey.shared.ui.auth.LoginScreen
import br.com.tavaressan.cafey.shared.ui.auth.RegisterScreen
import br.com.tavaressan.cafey.shared.ui.care.CareScreen
import br.com.tavaressan.cafey.shared.ui.device.DeviceRegisterScreen
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
private const val ROUTE_CARE = "care"
private const val ROUTE_DEVICE_REGISTER = "device_register"

/** Abas do rodapé principal — espelha `assets/nav.js` do protótipo, exceto "Base" (detalhes de
 * hardware do dispositivo), que não tem issue nem tela correspondente ainda. */
private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

private val BOTTOM_TABS = listOf(
    BottomTab(ROUTE_HOME, "Início", CafeyNavIcons.Home),
    BottomTab(ROUTE_SCHEDULE, "Agenda", CafeyNavIcons.Schedule),
    BottomTab(ROUTE_HISTORY, "Ritmo", CafeyNavIcons.Rhythm),
    BottomTab(ROUTE_CARE, "Cuidados", CafeyNavIcons.Care),
)

/**
 * Navegação entre login, cadastro e as telas principais do app (APP-03 a APP-07). Antes de decidir
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

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(CafeyTheme.colors.ground),
    ) {
        val sizeClass = navShellSizeClassFor(maxWidth)
        val contentMaxWidth = contentMaxWidthFor(sizeClass, compactMax = maxContentWidth)

        // NavShellScaffold é chamado a partir de uma única posição na árvore de composição,
        // independente de `sizeClass` — issue #187: antes, `NavHost` vivia dentro de um `if/else`
        // com dois pontos de invocação estruturalmente diferentes (Compact vs Medium/Expanded), o
        // que fazia o Compose descartar e recriar toda a subárvore do NavHost a cada travessia do
        // breakpoint de 768.dp (perdendo `rememberSaveable` de telas como o editor de agendamentos).
        // Disponibiliza o sizeClass calculado aqui (largura total da janela) para as telas de
        // conteúdo via CompositionLocal (issue #188) — telas não podem recalculá-lo localmente, ver
        // comentário em LocalNavShellSizeClass.
        CompositionLocalProvider(LocalNavShellSizeClass provides sizeClass) {
            NavShellScaffold(
                sizeClass = sizeClass,
                currentRoute = currentRoute,
                contentMaxWidth = contentMaxWidth,
                onSelectTab = { route -> navigateToTab(navController, route) },
            ) {
                NavHost(
                    navController = navController,
                    startDestination = startRoute,
                    modifier = Modifier.fillMaxSize(),
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
                    composable(ROUTE_HOME) {
                        HomeScreen(onGoToDeviceRegister = { navController.navigate(ROUTE_DEVICE_REGISTER) })
                    }
                    composable(ROUTE_SCHEDULE) { ScheduleScreen() }
                    composable(ROUTE_HISTORY) { HistoryScreen() }
                    composable(ROUTE_CARE) { CareScreen() }
                    composable(ROUTE_DEVICE_REGISTER) {
                        DeviceRegisterScreen(onRegistered = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}

/**
 * Casca de navegação (barra inferior no Compact, navegação lateral em Medium/Expanded) em torno de
 * [content]. [content] é chamado a partir de uma única posição de código, para que o Compose nunca
 * o trate como uma subárvore diferente ao alternar [sizeClass] (ver comentário em [CafeyNavHost]).
 * `internal` para ser exercitado por teste de composição em `desktopTest` (issue #187).
 */
@Composable
internal fun NavShellScaffold(
    sizeClass: NavShellSizeClass,
    currentRoute: String?,
    contentMaxWidth: Dp,
    onSelectTab: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val isCompact = sizeClass == NavShellSizeClass.Compact

    Row(modifier = Modifier.fillMaxSize()) {
        if (!isCompact && currentRoute != null) {
            CafeySideNav(sizeClass = sizeClass, currentRoute = currentRoute, onSelect = onSelectTab)
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Scaffold(
                modifier = Modifier.widthIn(max = contentMaxWidth).fillMaxHeight(),
                containerColor = CafeyTheme.colors.ground,
                bottomBar = {
                    if (isCompact && currentRoute != null) {
                        CafeyBottomBar(currentRoute = currentRoute, onSelect = onSelectTab)
                    }
                },
            ) { padding ->
                Box(modifier = Modifier.padding(padding)) { content() }
            }
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
                icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label, style = CafeyTheme.typography.caption) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = CafeyTheme.colors.brand,
                    unselectedIconColor = CafeyTheme.colors.muted,
                    selectedTextColor = CafeyTheme.colors.brand,
                    unselectedTextColor = CafeyTheme.colors.muted,
                    indicatorColor = CafeyTheme.colors.brandTint,
                ),
            )
        }
    }
}

/** Largura do trilho de ícones (tablet) — espelha `.rail` em `cafey.css`. */
private val RAIL_WIDTH = 82.dp

/** Largura da sidebar com rótulos (desktop) — espelha `.sidebar` em `cafey.css`. */
private val SIDEBAR_WIDTH = 238.dp

private val SideNavItemShape = RoundedCornerShape(14.dp)

/**
 * Navegação lateral para as faixas ≥768.dp: trilho de ícones estreito no tablet
 * ([NavShellSizeClass.Medium]) ou sidebar com rótulos no desktop ([NavShellSizeClass.Expanded]).
 * Substitui `CafeyBottomBar` nessas faixas (ver `.rail`/`.sidebar` em `cafey.css`).
 */
@Composable
private fun CafeySideNav(sizeClass: NavShellSizeClass, currentRoute: String, onSelect: (String) -> Unit) {
    val showLabel = sizeClass == NavShellSizeClass.Expanded
    val width = if (showLabel) SIDEBAR_WIDTH else RAIL_WIDTH

    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(CafeyTheme.colors.sunken)
            .padding(horizontal = if (showLabel) 18.dp else 15.dp, vertical = 26.dp),
        horizontalAlignment = if (showLabel) Alignment.Start else Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (showLabel) 3.dp else 8.dp),
    ) {
        BOTTOM_TABS.forEach { tab ->
            val selected = currentRoute == tab.route
            val itemModifier = Modifier
                .let { if (showLabel) it.fillMaxWidth() else it }
                .clip(SideNavItemShape)
                .background(if (selected) CafeyTheme.colors.surface else CafeyTheme.colors.sunken)
                .clickable { onSelect(tab.route) }
                .padding(horizontal = if (showLabel) 12.dp else 0.dp, vertical = if (showLabel) 11.dp else 12.dp)

            if (showLabel) {
                Row(
                    modifier = itemModifier,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        tint = if (selected) CafeyTheme.colors.brand else CafeyTheme.colors.muted,
                    )
                    Text(
                        text = tab.label,
                        style = CafeyTheme.typography.bodySmall,
                        color = if (selected) CafeyTheme.colors.ink else CafeyTheme.colors.ink4,
                    )
                }
            } else {
                Box(modifier = itemModifier.width(52.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (selected) CafeyTheme.colors.brand else CafeyTheme.colors.muted,
                    )
                }
            }
        }
    }
}
