package br.com.tavaressan.cafey.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import br.com.tavaressan.cafey.shared.network.platformDefaultBaseUrl
import br.com.tavaressan.cafey.shared.ui.CafeyNavHost
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme

/**
 * Raiz de composição compartilhada entre os três targets (Android, Desktop, Web): monta o
 * container de dependências, aplica o tema e entrega a navegação.
 *
 * Nos alvos que precisam de inicialização de plataforma (o Android precisa do `Context` para o
 * armazenamento seguro do token), essa inicialização acontece antes de chamar esta função.
 *
 * [baseUrl] permite que cada plataforma injete a URL do backend vinda de configuração de build
 * (ex.: `BuildConfig` no Android) em vez de depender só do padrão de desenvolvimento
 * ([platformDefaultBaseUrl]) — necessário para rodar num aparelho físico apontando para o backend
 * na rede local (issue #147).
 */
@Composable
fun App(baseUrl: String = platformDefaultBaseUrl) {
    val container = remember(baseUrl) { AppContainer(baseUrl) }
    CompositionLocalProvider(LocalAppContainer provides container) {
        CafeyTheme {
            CafeyNavHost()
        }
    }
}
