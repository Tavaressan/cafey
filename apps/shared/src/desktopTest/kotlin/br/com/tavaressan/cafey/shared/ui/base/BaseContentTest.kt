package br.com.tavaressan.cafey.shared.ui.base

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.tavaressan.cafey.shared.domain.model.DispositivoResponse
import br.com.tavaressan.cafey.shared.domain.model.PapelDispositivo
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme
import kotlin.test.Test
import kotlin.test.assertTrue

private fun fakeDevice(online: Boolean) = DispositivoResponse(
    id = "dev-1",
    nome = "Caféy Base",
    timezone = "America/Sao_Paulo",
    estado = "OCIOSO",
    online = online,
    ultimoVisto = null,
    versaoAgendamentos = 1,
    duracaoPreparoS = 120,
    limiarDescalcificacao = 60,
    contadorPreparos = 10,
    papel = PapelDispositivo.PROPRIETARIO,
    criadoEm = "2026-01-01T00:00:00Z",
    atualizadoEm = "2026-01-01T00:00:00Z",
)

/**
 * Regressão da issue #182: cobre [BaseContent] nos dois temas (`CafeyTheme(darkTheme = ...)`) e nos
 * dois estados de dispositivo (online/offline).
 */
@OptIn(ExperimentalTestApi::class)
class BaseContentTest {

    @Test
    fun showsOnlineStatusUnderLightTheme() = rendersStatus(darkTheme = false, online = true, expectedLabel = "Online")

    @Test
    fun showsOnlineStatusUnderDarkTheme() = rendersStatus(darkTheme = true, online = true, expectedLabel = "Online")

    @Test
    fun showsOfflineStatusUnderLightTheme() = rendersStatus(darkTheme = false, online = false, expectedLabel = "Offline")

    @Test
    fun showsOfflineStatusUnderDarkTheme() = rendersStatus(darkTheme = true, online = false, expectedLabel = "Offline")

    private fun rendersStatus(darkTheme: Boolean, online: Boolean, expectedLabel: String) = runComposeUiTest {
        setContent {
            CafeyTheme(darkTheme = darkTheme) {
                BaseContent(
                    loading = false,
                    device = fakeDevice(online = online),
                    darkTheme = false,
                    errorMessage = null,
                    onToggleTheme = {},
                )
            }
        }

        onNodeWithText(expectedLabel).assertExists()
    }

    @Test
    fun themeSwitchReflectsCurrentPreference() = runComposeUiTest {
        setContent {
            CafeyTheme {
                BaseContent(
                    loading = false,
                    device = fakeDevice(online = true),
                    darkTheme = false,
                    errorMessage = null,
                    onToggleTheme = {},
                )
            }
        }

        onNode(isToggleable()).assertIsOff()
    }

    @Test
    fun clickingTheSwitchInvokesOnToggleTheme() = runComposeUiTest {
        var toggled = false
        setContent {
            CafeyTheme {
                BaseContent(
                    loading = false,
                    device = fakeDevice(online = true),
                    darkTheme = false,
                    errorMessage = null,
                    onToggleTheme = { toggled = true },
                )
            }
        }

        onNode(isToggleable()).performClick()
        assertTrue(toggled, "onToggleTheme deveria ter sido chamado ao clicar no alternador")
    }
}
