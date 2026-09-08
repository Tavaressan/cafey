#pragma once

#include <cstddef>
#include <functional>
#include <string>
#include <utility>

#include "app/coffee_command.hpp"
#include "app/schedule_payload.hpp"
#include "core/ble_server.hpp"
#include "storage/schedule.hpp"

namespace cafey::app {

/**
 * @brief Servico BLE da cafeteira (FW-17, spec §6.4): expoe as caracteristicas
 * de comando (write), estado (read/notify) e agendamentos (write).
 *
 * A camada BLE apenas traduz bytes <-> chamada: a regra de negocio fica nos
 * handlers de dominio injetados (`on_command`, `on_schedules`), que sao os
 * mesmos acionados pelo caminho MQTT — sem duplicar logica. A dependencia da
 * stack NimBLE fica atras de cafey::core::IBleServer.
 */
class BleService {
public:
    // UUIDs 128-bit do servico cafey. Base: 6b3dXXXX-0b0b-4a3a-9c0e-cafe1a000000.
    static constexpr const char* kServiceUuid = "6b3d0001-0b0b-4a3a-9c0e-cafe1a000000";
    static constexpr const char* kCommandUuid = "6b3d0002-0b0b-4a3a-9c0e-cafe1a000000";
    static constexpr const char* kStateUuid = "6b3d0003-0b0b-4a3a-9c0e-cafe1a000000";
    static constexpr const char* kScheduleUuid = "6b3d0004-0b0b-4a3a-9c0e-cafe1a000000";

    // Limite alinhado a Agendador::kMaxSchedules / ScheduleStore::kMaxSchedules.
    static constexpr size_t kMaxSchedules = 16;

    using CommandHandler = std::function<void(const CoffeeCommand&)>;
    using ScheduleHandler =
        std::function<void(const cafey::storage::Schedule*, size_t, const SchedulePayloadMeta&)>;

    BleService(cafey::core::IBleServer& server, CommandHandler on_command,
               ScheduleHandler on_schedules)
        : server_(server),
          on_command_(std::move(on_command)),
          on_schedules_(std::move(on_schedules)) {}

    /** @brief Registra as caracteristicas e inicia o advertising. */
    bool begin(const std::string& device_name) {
        server_.add_characteristic(
            kCommandUuid, cafey::core::kBleWrite,
            [this](const std::string& value) { handle_command_write(value); });
        server_.add_characteristic(kStateUuid,
                                   cafey::core::kBleRead | cafey::core::kBleNotify, nullptr);
        server_.add_characteristic(
            kScheduleUuid, cafey::core::kBleWrite,
            [this](const std::string& value) { handle_schedule_write(value); });
        return server_.start(device_name);
    }

    /**
     * @brief Atualiza a caracteristica de estado (read/notify) com o mesmo corpo
     * publicado no topico `dispositivos/{id}/estado`.
     */
    bool publish_state(const std::string& state_json) {
        return server_.update_value(kStateUuid, state_json);
    }

private:
    void handle_command_write(const std::string& value) {
        const CoffeeCommand cmd = parse_coffee_command(value);
        if (cmd.action != CoffeeAction::Unknown && on_command_) {
            on_command_(cmd);
        }
    }

    void handle_schedule_write(const std::string& value) {
        cafey::storage::Schedule list[kMaxSchedules];
        size_t count = 0;
        SchedulePayloadMeta meta{};
        if (parse_schedule_payload(value, list, kMaxSchedules, &count, &meta) && on_schedules_) {
            on_schedules_(list, count, meta);
        }
    }

    cafey::core::IBleServer& server_;
    CommandHandler on_command_;
    ScheduleHandler on_schedules_;
};

} // namespace cafey::app
