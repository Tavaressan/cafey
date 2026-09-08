#pragma once

#include <cstdint>
#include <string>

namespace cafey::app {

/** @brief Acao de operacao da cafeteira (spec-backend §5.1: LIGAR/DESLIGAR/CANCELAR). */
enum class CoffeeAction : uint8_t { Unknown, Ligar, Desligar, Cancelar };

/**
 * @brief Comando de operacao ja interpretado, independente do transporte
 * (downlink MQTT `dispositivos/{id}/comando` ou caracteristica BLE de comando).
 */
struct CoffeeCommand {
    CoffeeAction action = CoffeeAction::Unknown;
    uint32_t duracao_s = 0;
    bool has_duracao = false;
};

/**
 * @brief Faz o parse do payload de comando compartilhado entre o downlink MQTT
 * e a caracteristica BLE (FW-17, spec-backend §6.2). Campo `acao` obrigatorio;
 * `duracaoS` opcional. Acao desconhecida/ausente resulta em
 * CoffeeAction::Unknown (o chamador ignora).
 */
CoffeeCommand parse_coffee_command(const std::string& json);

} // namespace cafey::app
