package br.com.tavaressan.cafey.shared.ui.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.tavaressan.cafey.shared.domain.model.DeviceState
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme
import kotlin.test.Test

/** Issue #376 — tempo restante dentro do mostrador. */
@OptIn(ExperimentalTestApi::class)
class BrewingDialTest {

    private fun androidx.compose.ui.test.ComposeUiTest.dial(progress: BrewingProgress, estado: DeviceState = DeviceState.Brewing, estimado: Boolean = false) =
        setContent {
            CafeyTheme {
                BrewingDial(
                    stateLabel = if (estado == DeviceState.Brewing) "Preparando" else "Café pronto",
                    starColor = Color.Gray,
                    deviceState = estado,
                    brewingProgress = progress,
                    duracaoPreparoS = 300,
                    estimado = estimado,
                )
            }
        }

    private fun determinado(restanteS: Long) = BrewingProgress.Determinate(
        fraction = 1f - restanteS / 300f,
        restanteS = restanteS,
        terminaEmMs = 1_700_000_000_000L,
    )

    @Test
    fun mostraOTempoRestanteELegendaDoEstado() = runComposeUiTest {
        dial(determinado(272))
        onNodeWithText("4:32").assertExists()
        onNodeWithText("Preparando").assertExists()
    }

    @Test
    fun tocarAlternaParaOHorarioPrevisto() = runComposeUiTest {
        dial(determinado(272))
        onNodeWithText("4:32").performClick()
        onNodeWithText("pronto às").assertExists()
        onNodeWithText("4:32").assertDoesNotExist()
        onNodeWithText("pronto às").performClick()
        onNodeWithText("4:32").assertExists()
    }

    @Test
    fun abaixoDeUmMinutoMostraMenosDeUmMinuto() = runComposeUiTest {
        mainClock.autoAdvance = false // o halo do ultimo minuto anima para sempre
        dial(determinado(40))
        onNodeWithText("menos de 1 min").assertExists()
    }

    @Test
    fun offlineMarcaOTempoComoEstimado() = runComposeUiTest {
        dial(determinado(272), estimado = true)
        onNodeWithText("~4:32").assertExists()
    }

    @Test
    fun semDadosDeInicioNaoMostraTempoNenhum() = runComposeUiTest {
        mainClock.autoAdvance = false // o anel indeterminado gira para sempre
        dial(BrewingProgress.Indeterminate)
        onNodeWithText("Preparando").assertExists()
        onNodeWithText("agora").assertDoesNotExist()
    }

    @Test
    fun cafeProntoVoltaAoRotuloDoEstado() = runComposeUiTest {
        dial(BrewingProgress.Inactive, estado = DeviceState.Ready)
        onNodeWithText("Café pronto").assertExists()
    }
}
