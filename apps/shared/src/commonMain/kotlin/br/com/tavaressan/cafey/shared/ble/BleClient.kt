package br.com.tavaressan.cafey.shared.ble

import br.com.tavaressan.cafey.shared.domain.model.AcaoComando
import br.com.tavaressan.cafey.shared.domain.model.BleEvento
import br.com.tavaressan.cafey.shared.domain.model.ComandoResponse
import br.com.tavaressan.cafey.shared.domain.model.EventoResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Erros de comunicação BLE — mesma ideia de [br.com.tavaressan.cafey.shared.network.ApiError],
 * mas para o transporte local (spec §6.4/§6.5 e UC-04 #249). */
sealed class BleError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** Nenhuma base BLE anunciando o serviço foi encontrada nas proximidades. */
    data object BaseNaoEncontrada : BleError("Nenhuma base BLE encontrada nas proximidades")

    /** A base foi encontrada, mas a conexão/característica falhou. */
    class ConexaoFalhou(cause: Throwable) : BleError("Falha ao conectar por BLE à base", cause)

    /** Timeout na comunicação BLE ou na resposta da cafeteira. */
    class Timeout(motivo: String = "Tempo limite excedido na comunicação BLE") : BleError(motivo)

    /** Erro retornado pela base durante provisionamento Wi-Fi (ex.: senha incorreta). */
    class ProvisionamentoFalhou(val motivo: String) : BleError("Falha no provisionamento: $motivo")
}

/**
 * Cliente BLE para a base (ESP32) — implementado por plataforma (Android/iOS), fora do escopo
 * deste módulo comum. Cobre o serviço anunciado continuamente pelo ESP32 com características de
 * comando, estado e agendamentos (spec §6.4), a fila de eventos pendentes usada pelo proxy
 * (spec §6.5) e o provisionamento de credenciais Wi-Fi (UC-04 #249).
 */
interface BleClient {

    /** Indica se o cliente BLE é suportado na plataforma atual (ex.: Android = true, Desktop/Web = false). */
    val isSupported: Boolean get() = true

    /** Executa o comando diretamente na base, sem passar pela nuvem (spec §6.4, passo 3). */
    suspend fun enviarComando(dispositivoId: String, acao: AcaoComando)

    /** Lê a fila de eventos de preparo ainda não confirmados na base (spec §6.5, passo 1). */
    suspend fun lerEventosPendentes(dispositivoId: String): List<BleEvento>

    /** Confirma a entrega ao backend, permitindo que a base limpe a fila local (spec §6.5, passo 3). */
    suspend fun confirmarEventosEntregues(dispositivoId: String, eventoIds: List<String>)

    /** Escaneia bases Cafey nas proximidades (UC-04, RF-03). */
    fun escanearDispositivos(): Flow<List<BleDispositivo>> = emptyFlow()

    /** Lê identificação da máquina via kDeviceInfoUuid (UC-04, RF-03). */
    suspend fun lerInfoDispositivo(dispositivoId: String): DispositivoInfo =
        throw BleError.BaseNaoEncontrada

    /** Envia credenciais Wi-Fi e observa status até CONNECTED ou erro (UC-04, RF-04). */
    suspend fun provisionarWifi(
        dispositivoId: String,
        ssid: String,
        password: String,
    ): Flow<WifiProvisionStatus> = emptyFlow()
}

/**
 * Recorte de [br.com.tavaressan.cafey.shared.network.CommandApi] usado por [BleCommandFallback] —
 * existe para permitir testar o fallback com um fake, sem HTTP real.
 * `CommandApi` implementa esta interface.
 */
interface ComandoRemoto {
    suspend fun ligar(dispositivoId: String): ComandoResponse
    suspend fun desligar(dispositivoId: String): ComandoResponse
    suspend fun cancelar(dispositivoId: String): ComandoResponse
}

/**
 * Recorte de [br.com.tavaressan.cafey.shared.network.EventApi] usado por [BleEventProxy] — mesma
 * razão de [ComandoRemoto]. `EventApi` implementa esta interface.
 */
interface EventoProxyRemoto {
    suspend fun enviarProxyBle(dispositivoId: String, eventos: List<BleEvento>): List<EventoResponse>
}
