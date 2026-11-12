#pragma once

#include <cstdint>
#include <string>

namespace cafey::app {

/** @brief Acao de operacao da cafeteira (spec-backend §5.1 e FW-30). */
enum class CoffeeAction : uint8_t {
    Unknown,
    Ligar,
    Desligar,
    Cancelar,
    EsquecerRede,       // requer `ssid`; remove rede salva (e desconecta se for a atual)
    EsquecerTodasRedes, // remove todas as redes; base fica aguardando provisionamento BLE
    Silenciar,          // modo silencioso do buzzer, persistido em NVS (#311)
    AtivarSom,          // sai do modo silencioso (#311)
};

/**
 * @brief Comando de operacao ja interpretado, independente do transporte
 * (downlink MQTT `dispositivos/{id}/comando` ou caracteristica BLE de comando).
 */
struct CoffeeCommand {
    CoffeeAction action = CoffeeAction::Unknown;
    uint32_t duracao_s = 0;
    bool has_duracao = false;
    std::string ssid; // usado por EsquecerRede
};

/**
 * @brief Faz o parse do payload de comando compartilhado entre o downlink MQTT
 * e a caracteristica BLE (FW-17/FW-30, spec-backend §6.2). Campo `acao`
 * obrigatorio; `duracaoS` e `ssid` opcionais conforme a acao. Acao
 * desconhecida/ausente resulta em CoffeeAction::Unknown (o chamador ignora).
 */
CoffeeCommand parse_coffee_command(const std::string& json);

} // namespace cafey::app
