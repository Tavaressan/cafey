package br.com.tavaressan.cafey.shared.ble

import br.com.tavaressan.cafey.shared.domain.model.AcaoComando
import br.com.tavaressan.cafey.shared.domain.model.BleEvento

/** Dublê de [BleClient] para os testes de [BleCommandFallback] e [BleEventProxy]. */
class FakeBleClient(
    private val eventosPendentes: List<BleEvento> = emptyList(),
    private val falharAoConectar: Boolean = false,
) : BleClient {
    val comandosEnviados = mutableListOf<Pair<String, AcaoComando>>()
    val confirmacoes = mutableListOf<Pair<String, List<String>>>()

    override suspend fun enviarComando(dispositivoId: String, acao: AcaoComando) {
        if (falharAoConectar) throw BleError.BaseNaoEncontrada
        comandosEnviados += dispositivoId to acao
    }

    override suspend fun lerEventosPendentes(dispositivoId: String): List<BleEvento> = eventosPendentes

    override suspend fun confirmarEventosEntregues(dispositivoId: String, eventoIds: List<String>) {
        confirmacoes += dispositivoId to eventoIds
    }
}
