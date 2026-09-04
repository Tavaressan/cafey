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
    // Agendador
    TimeSynced,
    ScheduleFired,
};

/**
 * @brief Mensagem POD trocada via fila do Active Object.
 * Payload pequeno e trivialmente copiavel (a fila do FreeRTOS copia bytes).
 */
struct Message {
    MessageType type = MessageType::None;
    int32_t arg = 0;
};

} // namespace cafey::core
