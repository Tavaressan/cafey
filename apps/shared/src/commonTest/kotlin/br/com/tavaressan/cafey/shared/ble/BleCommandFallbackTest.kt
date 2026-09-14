package br.com.tavaressan.cafey.shared.ble

import br.com.tavaressan.cafey.shared.domain.model.AcaoComando
import br.com.tavaressan.cafey.shared.domain.model.ComandoResponse
import br.com.tavaressan.cafey.shared.network.ApiError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val DEVICE_ID = "device-1"

/** Dublê de [ComandoRemoto] — simula a nuvem respondendo ou falhando por conectividade. */
private class FakeComandoRemoto(private val falharComRede: Boolean) : ComandoRemoto {
    var chamadas = 0

    override suspend fun ligar(dispositivoId: String): ComandoResponse = responder()
    override suspend fun desligar(dispositivoId: String): ComandoResponse = responder()
    override suspend fun cancelar(dispositivoId: String): ComandoResponse = responder()

    private fun responder(): ComandoResponse {
        chamadas++
        if (falharComRede) throw ApiError.Network(Exception("sem conexão"))
        return ComandoResponse(
            comandoId = "cmd-1",
            acao = AcaoComando.LIGAR,
            duracaoS = 30,
            emitidoEm = "2026-01-01T00:00:00Z",
        )
    }
}

/** Reproduz o fallback de comando (spec §6.4): nuvem primeiro, BLE só sem resposta de rede. */
class BleCommandFallbackTest {

    @Test
    fun nuvemResponde_naoTentaBle() = runTest {
        val commandApi = FakeComandoRemoto(falharComRede = false)
        val bleClient = FakeBleClient()
        val fallback = BleCommandFallback(commandApi, bleClient)

        val resultado = fallback.executar(DEVICE_ID, AcaoComando.LIGAR)

        assertIs<ComandoResultado.ViaNuvem>(resultado)
        assertTrue(bleClient.comandosEnviados.isEmpty())
    }

    @Test
    fun nuvemSemResposta_caiParaBle() = runTest {
        val commandApi = FakeComandoRemoto(falharComRede = true)
        val bleClient = FakeBleClient()
        val fallback = BleCommandFallback(commandApi, bleClient)

        val resultado = fallback.executar(DEVICE_ID, AcaoComando.DESLIGAR)

        assertIs<ComandoResultado.ViaBle>(resultado)
        assertEquals(listOf(DEVICE_ID to AcaoComando.DESLIGAR), bleClient.comandosEnviados)
    }

    @Test
    fun nuvemComErroHttp_naoCaiParaBle() = runTest {
        val commandApi = object : ComandoRemoto {
            override suspend fun ligar(dispositivoId: String): ComandoResponse =
                throw ApiError.Http(400, null)

            override suspend fun desligar(dispositivoId: String): ComandoResponse =
                throw ApiError.Http(400, null)

            override suspend fun cancelar(dispositivoId: String): ComandoResponse =
                throw ApiError.Http(400, null)
        }
        val bleClient = FakeBleClient()
        val fallback = BleCommandFallback(commandApi, bleClient)

        assertFailsWith<ApiError.Http> { fallback.executar(DEVICE_ID, AcaoComando.CANCELAR) }
        assertTrue(bleClient.comandosEnviados.isEmpty())
    }
}
