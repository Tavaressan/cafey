package br.com.tavaressan.cafey.shared.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.tavaressan.cafey.shared.LocalAppContainer
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme

/** Issue #183 — tela de Conta (fora do escopo do protótipo original, `nav.js` não a lista) com a
 * ação "Sair" que faltava para `ApiClient.logout()`. Alcançável a partir de "Base" (não é uma aba
 * do rodapé/trilho/sidebar, para não divergir das 5 abas espelhadas de `nav.js` na issue #182). */
@Composable
fun AccountScreen(onLoggedOut: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = viewModel<AccountViewModel>(
        factory = viewModelFactory { initializer { AccountViewModel(container.apiClient) } },
    )
    val state by viewModel.uiState.collectAsState()

    // Navegar é efeito colateral: chamar no corpo do composable dispararia a cada recomposição
    // (mesmo padrão de LoginScreen, LaunchedEffect(state.success)).
    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLoggedOut()
    }

    AccountContent(onLogout = viewModel::logout)
}

/** Corpo visual, separado de [AccountScreen] para ser testável por composição sem
 * `AppContainer`/rede (mesmo padrão de `BaseContent`, issue #182). */
@Composable
internal fun AccountContent(onLogout: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(CafeyTheme.colors.ground).padding(22.dp)) {
        Text("Conta", style = CafeyTheme.typography.screenTitle, color = CafeyTheme.colors.ink)

        Button(
            onClick = onLogout,
            colors = ButtonDefaults.buttonColors(containerColor = CafeyTheme.colors.brand),
            shape = CafeyTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        ) {
            Text("Sair", color = CafeyTheme.colors.brandOn)
        }
    }
}
