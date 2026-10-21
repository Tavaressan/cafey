package br.com.tavaressan.cafey.shared.ble

import br.com.tavaressan.cafey.shared.domain.model.AcaoComando
import br.com.tavaressan.cafey.shared.domain.model.BleEvento
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/** Dublê de [BleClient] para os testes de [BleCommandFallback], [BleEventProxy] e provisionamento Wi-Fi (UC-04 #249). */
class FakeBleClient(
    private val eventosPendentes: List<BleEvento> = emptyList(),
    private val falharAoConectar: Boolean = false,
    override val isSupported: Boolean = true,
    var dispositivosEscaneados: List<BleDispositivo> = listOf(
        BleDispositivo(id = "cafe-01", nome = "Cafey Express", rssi = -65)
    ),
    var infoDispositivoRetorno: DispositivoInfo = DispositivoInfo(serial = "cafey-proto-01"),
    var statusProvisionamentoRetorno: List<WifiProvisionStatus> = listOf(
        WifiProvisionStatus(WifiProvisionState.CONNECTING),
        WifiProvisionStatus(WifiProvisionState.CONNECTED, ip = "192.168.1.150")
    ),
    var erroAoLerInfo: Throwable? = null,
    var erroAoProvisionar: Throwable? = null,
) : BleClient {
    val comandosEnviados = mutableListOf<Pair<String, AcaoComando>>()
    val confirmacoes = mutableListOf<Pair<String, List<String>>>()
    val credenciaisProvisionadas = mutableListOf<Triple<String, String, String>>()

    override suspend fun enviarComando(dispositivoId: String, acao: AcaoComando) {
        if (falharAoConectar) throw BleError.BaseNaoEncontrada
        comandosEnviados += dispositivoId to acao
    }

    override suspend fun lerEventosPendentes(dispositivoId: String): List<BleEvento> = eventosPendentes

    override suspend fun confirmarEventosEntregues(dispositivoId: String, eventoIds: List<String>) {
        confirmacoes += dispositivoId to eventoIds
    }

    override fun escanearDispositivos(): Flow<List<BleDispositivo>> = flowOf(dispositivosEscaneados)

    override suspend fun lerInfoDispositivo(dispositivoId: String): DispositivoInfo {
        if (falharAoConectar) throw BleError.BaseNaoEncontrada
        erroAoLerInfo?.let { throw it }
        return infoDispositivoRetorno
    }

    override suspend fun provisionarWifi(
        dispositivoId: String,
        ssid: String,
        password: String,
    ): Flow<WifiProvisionStatus> {
        if (falharAoConectar) throw BleError.BaseNaoEncontrada
        erroAoProvisionar?.let { throw it }
        credenciaisProvisionadas += Triple(dispositivoId, ssid, password)
        return flow {
            for (status in statusProvisionamentoRetorno) {
                emit(status)
            }
        }
    }
}
