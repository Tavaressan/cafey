package br.com.tavaressan.cafey.shared.ble

import br.com.tavaressan.cafey.shared.domain.model.AcaoComando
import br.com.tavaressan.cafey.shared.domain.model.ComandoResponse
import br.com.tavaressan.cafey.shared.network.ApiError

/** Resultado de [BleCommandFallback.executar]: por qual caminho o comando foi de fato executado. */
sealed class ComandoResultado {
    data class ViaNuvem(val resposta: ComandoResponse) : ComandoResultado()
    data object ViaBle : ComandoResultado()
}

/**
 * Fallback de comando (spec §6.4): o app tenta a nuvem primeiro; sem resposta (falha de rede),
 * conecta por BLE diretamente à base — que está a poucos metros — e executa o comando localmente.
 *
 * Falhas HTTP (4xx/5xx, [ApiError.Http]) **não** caem para BLE: são erros de domínio (ex.: comando
 * inválido, sessão expirada), não de conectividade, e a base provavelmente os recusaria também.
 */
class BleCommandFallback(
    private val commandApi: ComandoRemoto,
    private val bleClient: BleClient,
) {
    suspend fun executar(dispositivoId: String, acao: AcaoComando): ComandoResultado =
        try {
            ComandoResultado.ViaNuvem(enviarPorNuvem(dispositivoId, acao))
        } catch (e: ApiError.Network) {
            bleClient.enviarComando(dispositivoId, acao)
            ComandoResultado.ViaBle
        }

    private suspend fun enviarPorNuvem(dispositivoId: String, acao: AcaoComando): ComandoResponse =
        when (acao) {
            AcaoComando.LIGAR -> commandApi.ligar(dispositivoId)
            AcaoComando.DESLIGAR -> commandApi.desligar(dispositivoId)
            AcaoComando.CANCELAR -> commandApi.cancelar(dispositivoId)
        }
}
