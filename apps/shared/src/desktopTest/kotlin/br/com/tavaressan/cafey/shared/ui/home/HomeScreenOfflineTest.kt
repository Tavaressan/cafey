package br.com.tavaressan.cafey.shared.ui.home

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import br.com.tavaressan.cafey.shared.domain.model.DeviceState
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class HomeScreenOfflineTest {

    @Test
    fun offlineBanner_displaysDisconnectedMessage() = runComposeUiTest {
        setContent {
            CafeyTheme {
                OfflineBanner()
            }
        }

        onNodeWithText("Cafeteira desconectada da nuvem").assertExists()
    }

    @Test
    fun stageCard_disablesButtonAndShowsExplanation_whenOffline() = runComposeUiTest {
        setContent {
            CafeyTheme {
                StageCard(
                    deviceState = DeviceState.Idle,
                    commandInFlight = false,
                    canSendCommand = false,
                    isOffline = true,
                    onPrepare = {},
                    onCancel = {},
                )
            }
        }

        onNodeWithText("Preparar agora").assertIsNotEnabled()
        onNodeWithText("Cafeteira desconectada. Comandos indisponíveis.").assertExists()
    }
}
