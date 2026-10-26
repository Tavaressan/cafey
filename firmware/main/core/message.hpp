#pragma once

#include <cstdint>

namespace cafey::core {

/**
 * @brief Tipos de evento trocados entre Active Objects.
 * Comunicacao 100% assincrona: nenhum AO acessa estado de outro.
 */
enum class MessageType : uint8_t {
    None = 0,
    // Cafeteira
    ButtonPressed,
    StartBrew,
    StopBrew,
    BrewFinished,
    // Conectividade
    WifiConnected,
    WifiDisconnected,
    CommandReceived,
    MqttConnected,
    MqttDisconnected,
    EstadoChanged, // arg: 1 = preparando, 0 = desligada (FW-21)
    EventQueued,   // novo evento enfileirado no storage (FW-22)
    // Agendador
    TimeSynced,
    ScheduleFired,
    // Estado do LED / Sistema (spec §5.3)
    NetworkStatusChanged, // arg: 1 = online, 0 = offline
    ScheduleArmedChanged, // arg: 1 = armado, 0 = sem agendamento
};

/**
 * @brief Mensagem POD trocada via fila do Active Object.
 * Payload pequeno e trivialmente copiavel (a fila do FreeRTOS copia bytes).
 */
struct Message {
    MessageType type = MessageType::None;
    int32_t arg = 0;
    int32_t arg2 = 0;
};

} // namespace cafey::core
