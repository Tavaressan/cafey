package br.com.tavaressan.cafey.shared.ble

/**
 * Critério unificado de filtragem e identificação de cafeteiras Cafey via BLE (issue #301).
 *
 * Utilizado tanto na varredura ativa de anúncios BLE quanto na inspeção de periféricos já
 * conectados via GATT no Android, garantindo que o critério de identificação seja consistente
 * e testável em [commonTest].
 */
object BleDeviceFilter {

    /**
     * Determina se um dispositivo é uma cafeteira Cafey com base no seu nome e/ou lista de
     * UUIDs de serviço anunciados/oferecidos.
     *
     * Regras:
     * 1. Se [name] contiver "cafey" (case-insensitive), é considerado uma cafeteira Cafey.
     * 2. Se algum dos [serviceUuids] corresponder ao serviço principal [BleUuids.SERVICE]
     *    (comparação case-insensitive), é considerado uma cafeteira Cafey.
     * 3. Dispositivos sem nome válido e sem correspondência de UUID não são aceitos.
     */
    fun isTargetDevice(name: String?, serviceUuids: List<String> = emptyList()): Boolean {
        if (!name.isNullOrBlank() && name.contains("cafey", ignoreCase = true)) {
            return true
        }
        val targetServiceUuid = BleUuids.SERVICE.lowercase()
        return serviceUuids.any { it.lowercase() == targetServiceUuid }
    }

    /**
     * Retorna o nome amigável a ser exibido para o dispositivo, utilizando [name] se presente e
     * não em branco, ou caindo para o nome padrão "Cafey" caso o dispositivo tenha sido
     * identificado pelo serviço UUID.
     */
    fun resolveDisplayName(name: String?): String {
        return name?.trim()?.takeIf { it.isNotEmpty() } ?: "Cafey"
    }
}
