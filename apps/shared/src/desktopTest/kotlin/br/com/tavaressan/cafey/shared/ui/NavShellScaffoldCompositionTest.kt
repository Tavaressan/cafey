package br.com.tavaressan.cafey.shared.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regressão da issue #187: antes do fix, `NavHost` (aqui representado por [content]) era chamado a
 * partir de dois pontos de composição estruturalmente diferentes — um `if`/`else` ramificado por
 * `NavShellSizeClass` — o que fazia o Compose descartar e recriar toda a subárvore ao cruzar o
 * breakpoint de 768.dp (perdendo `rememberSaveable`/`LaunchedEffect`/`DisposableEffect` do
 * conteúdo ativo, ex.: o editor de agendamentos em `ScheduleScreen`).
 *
 * Um spike com o `NavHost` real de `androidx.navigation.compose` (removido após a investigação)
 * confirmou empiricamente essa recriação de subárvore, mas também mostrou que ViewModels obtidos
 * via `viewModel()` com escopo de `NavBackStackEntry` sobrevivem a ela — o `ViewModelStore` da
 * entry fica fora da composição, ancorado no `LocalViewModelStoreOwner` ambiente (estável entre os
 * branches). Ou seja: a instabilidade estrutural é real, mas não explica sozinha a rajada de
 * chamadas de rede relatada na issue (ver handback para o coordinator).
 *
 * Este teste cobre [NavShellScaffold] — a extração que unificou `content` (o `NavHost` real) em uma
 * única posição de composição — usando um `DisposableEffect` sentinela em vez do `NavHost`/DI
 * completos, para não precisar de rede/`AppContainer` fake.
 */
@OptIn(ExperimentalTestApi::class)
class NavShellScaffoldCompositionTest {

    @Test
    fun contentIsNotDisposedWhenSizeClassChanges() = runComposeUiTest {
        var disposals = 0
        var sizeClass by mutableStateOf(NavShellSizeClass.Compact)

        setContent {
            CafeyTheme {
                NavShellScaffold(
                    sizeClass = sizeClass,
                    currentRoute = "home",
                    contentMaxWidth = 390.dp,
                    onSelectTab = {},
                ) {
                    DisposableEffect(Unit) { onDispose { disposals++ } }
                    Text("content", modifier = Modifier.fillMaxSize())
                }
            }
        }

        waitForIdle()
        assertEquals(0, disposals, "nao deveria ter descartado o conteudo na composicao inicial")

        sizeClass = NavShellSizeClass.Medium
        waitForIdle()
        assertEquals(0, disposals, "conteudo foi descartado ao trocar de Compact para Medium")

        sizeClass = NavShellSizeClass.Expanded
        waitForIdle()
        assertEquals(0, disposals, "conteudo foi descartado ao trocar de Medium para Expanded")

        sizeClass = NavShellSizeClass.Compact
        waitForIdle()
        assertEquals(0, disposals, "conteudo foi descartado ao voltar de Expanded para Compact")
    }
}
