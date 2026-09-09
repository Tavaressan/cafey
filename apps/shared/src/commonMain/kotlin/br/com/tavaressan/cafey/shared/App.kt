package br.com.tavaressan.cafey.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme

/**
 * Raiz de composição compartilhada entre os três targets (Android, Desktop, Web).
 * A navegação real (login → cadastro → início) chega em APP-03/APP-04; por enquanto
 * só prova que `CafeyTheme` está disponível ponta a ponta.
 */
@Composable
fun App() {
    CafeyTheme {
        Box(
            modifier = Modifier.fillMaxSize().background(CafeyTheme.colors.ground),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "Caféy", style = CafeyTheme.typography.screenTitle)
        }
    }
}
