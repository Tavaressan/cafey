package br.com.tavaressan.cafey.shared.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regressão da issue #68 (APP-09 — Desktop no nível essencial): o Desktop precisa limitar a
 * largura do conteúdo à mesma regra do Web (390dp, largura mobile do protótipo), em vez de esticar
 * em viewports largas. É esse limite — e o reaproveitamento do restante do `shared` (login,
 * operação, agendamento, histórico via [CafeyNavHost]) — que caracteriza o nível "essencial" do
 * cliente Desktop.
 */
class PlatformLayoutTest {

    @Test
    fun maxContentWidth_matchesWebParity() {
        assertEquals(390.dp, maxContentWidth)
    }
}
