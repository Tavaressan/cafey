package br.com.tavaressan.cafey.shared.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CafeyNavIconsTest {

    @Test
    fun allTabIconsHaveVisibleStrokePaths() {
        val icons = listOf(CafeyNavIcons.Home, CafeyNavIcons.Schedule, CafeyNavIcons.Rhythm, CafeyNavIcons.Care)

        icons.forEach { icon ->
            assertEquals(21.dp, icon.defaultWidth, "icone ${icon.name} deveria ter 21dp de largura, como no design")
            assertEquals(21.dp, icon.defaultHeight, "icone ${icon.name} deveria ter 21dp de altura, como no design")
            assertTrue(icon.root.size > 0, "icone ${icon.name} nao pode ficar vazio (regressao do icon = {})")
        }
    }
}
