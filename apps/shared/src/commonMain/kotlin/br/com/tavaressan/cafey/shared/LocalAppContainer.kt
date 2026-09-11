package br.com.tavaressan.cafey.shared

import androidx.compose.runtime.staticCompositionLocalOf

/** Disponibiliza o [AppContainer] para toda a árvore de composição, montado uma vez no topo do app. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("LocalAppContainer não foi provido — envolva a raiz do app com CompositionLocalProvider(LocalAppContainer provides ...)")
}
