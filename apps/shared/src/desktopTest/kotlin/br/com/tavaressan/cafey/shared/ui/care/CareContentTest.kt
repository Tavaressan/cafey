package br.com.tavaressan.cafey.shared.ui.care

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.tavaressan.cafey.shared.domain.model.CuidadosResponse
import br.com.tavaressan.cafey.shared.domain.model.StatusCuidadoResponse
import br.com.tavaressan.cafey.shared.domain.model.StatusDescalcificacaoResponse
import br.com.tavaressan.cafey.shared.domain.model.TipoCuidado
import br.com.tavaressan.cafey.shared.ui.NavShellSizeClass
import br.com.tavaressan.cafey.shared.ui.theme.CafeyTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Números do protótipo (care.html): enxágue 34 de 40, filtro 210 de 300, descalcificação 60 de 400.
private fun cuidados(destaque: TipoCuidado = TipoCuidado.ENXAGUE) = CuidadosResponse(
    descalcificacao = StatusDescalcificacaoResponse(60, 400, false, 15.0),
    enxague = StatusCuidadoResponse(34, 40, false, 85.0),
    filtro = StatusCuidadoResponse(210, 300, false, 70.0),
    destaque = destaque,
)

private fun state(cuidados: CuidadosResponse = cuidados(), podeDarBaixa: Boolean = true) =
    CareUiState(loading = false, deviceId = "dev-1", podeDarBaixa = podeDarBaixa, cuidados = cuidados)

/** Issue #190 — a tela Cuidados mostra os três cartões, com o destaque no topo. */
@OptIn(ExperimentalTestApi::class)
class CareContentTest {

    @Test
    fun showsTheThreeCuidadosWithCounterAndLimiar() = runComposeUiTest {
        setContent {
            CafeyTheme { CareContent(state(), NavShellSizeClass.Compact, onDarBaixa = {}) }
        }

        onNodeWithText("Enxaguar o circuito").assertExists()
        onNodeWithText("Trocar o filtro de água").assertExists()
        onNodeWithText("Descalcificar").assertExists()
        onNodeWithText("34 de 40 preparos desde o último enxágue").assertExists()
        onNodeWithText("210 de 300 preparos").assertExists()
        onNodeWithText("60 de 400 preparos").assertExists()
    }

    @Test
    fun putsTheDestaqueFirstAndMarksOnlyItAsChegando() = runComposeUiTest {
        setContent {
            CafeyTheme { CareContent(state(cuidados(TipoCuidado.FILTRO)), NavShellSizeClass.Compact, onDarBaixa = {}) }
        }

        val filtroTop = onNodeWithTag("cuidado-FILTRO").getUnclippedBoundsInRoot().top
        val enxagueTop = onNodeWithTag("cuidado-ENXAGUE").getUnclippedBoundsInRoot().top
        val descalcificacaoTop = onNodeWithTag("cuidado-DESCALCIFICACAO").getUnclippedBoundsInRoot().top
        assertTrue(filtroTop < enxagueTop, "o destaque (filtro) deveria vir acima do enxágue")
        assertTrue(filtroTop < descalcificacaoTop, "o destaque (filtro) deveria vir acima da descalcificação")
        onAllNodesWithText("Chegando").assertCountEquals(1)
    }

    @Test
    fun alertsWhenTheLimiarIsReached() = runComposeUiTest {
        val noLimiar = cuidados().copy(enxague = StatusCuidadoResponse(40, 40, true, 100.0))
        setContent {
            CafeyTheme { CareContent(state(noLimiar), NavShellSizeClass.Compact, onDarBaixa = {}) }
        }

        onNodeWithText("Hora de enxaguar o circuito").assertExists()
        onNodeWithText("Enxaguar o circuito").assertDoesNotExist()
    }

    @Test
    fun ownerCanDarBaixaOnEachCuidado() = runComposeUiTest {
        val baixas = mutableListOf<TipoCuidado>()
        setContent {
            CafeyTheme { CareContent(state(), NavShellSizeClass.Compact, onDarBaixa = { baixas += it }) }
        }

        onAllNodesWithText("Já fiz isso").assertCountEquals(3)
        for (tipo in listOf(TipoCuidado.FILTRO, TipoCuidado.ENXAGUE, TipoCuidado.DESCALCIFICACAO)) {
            onNode(hasText("Já fiz isso") and hasAnyAncestor(hasTestTag("cuidado-${tipo.name}"))).performClick()
        }
        assertEquals(listOf(TipoCuidado.FILTRO, TipoCuidado.ENXAGUE, TipoCuidado.DESCALCIFICACAO), baixas)
    }

    @Test
    fun nonOwnerSeesNoBaixaButAnExplanationPerCuidado() = runComposeUiTest {
        setContent {
            CafeyTheme { CareContent(state(podeDarBaixa = false), NavShellSizeClass.Compact, onDarBaixa = {}) }
        }

        onAllNodesWithText("Já fiz isso").assertCountEquals(0)
        onNodeWithText("Só o proprietário do dispositivo pode registrar o enxágue.").assertExists()
        onNodeWithText("Só o proprietário do dispositivo pode registrar a troca do filtro.").assertExists()
        onNodeWithText("Só o proprietário do dispositivo pode registrar a descalcificação.").assertExists()
    }

    @Test
    fun placesTheTwoSecondaryCardsSideBySideOnWideScreens() = runComposeUiTest {
        setContent {
            CafeyTheme { CareContent(state(), NavShellSizeClass.Expanded, onDarBaixa = {}) }
        }

        val filtro = onNodeWithTag("cuidado-FILTRO").getUnclippedBoundsInRoot()
        val descalcificacao = onNodeWithTag("cuidado-DESCALCIFICACAO").getUnclippedBoundsInRoot()
        assertEquals(filtro.top, descalcificacao.top, "os cuidados secundários deveriam ficar na mesma linha")
        assertTrue(descalcificacao.left > filtro.left)
    }

    @Test
    fun showsEmptyStateWithoutDevice() = runComposeUiTest {
        setContent {
            CafeyTheme { CareContent(CareUiState(loading = false), NavShellSizeClass.Compact, onDarBaixa = {}) }
        }

        onNodeWithText("Nenhum dispositivo vinculado ainda.").assertExists()
    }
}
