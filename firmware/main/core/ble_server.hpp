#pragma once

#include <cstdint>
#include <functional>
#include <string>

namespace cafey::core {

/**
 * @brief Propriedades GATT de uma caracteristica (bitmask).
 */
enum BleCharProps : uint8_t {
    kBleRead = 1 << 0,
    kBleWrite = 1 << 1,
    kBleNotify = 1 << 2,
};

/** @brief Callback disparado quando um cliente BLE escreve numa caracteristica. */
using BleWriteCallback = std::function<void(const std::string& value)>;

/**
 * @brief Abstracao do servidor GATT BLE.
 *
 * Isola o firmware da stack NimBLE do ESP-IDF (componente `bt`), permitindo
 * testar em host a logica de comando/estado/agendamentos e do proxy de eventos
 * (FW-17/FW-18, spec §6.4/§6.5) sem radio real. Mesma estrategia de
 * core/mqtt_client.hpp + test/mock_mqtt_client.hpp.
 */
class IBleServer {
public:
    virtual ~IBleServer() = default;

    /**
     * @brief Registra uma caracteristica pelo UUID, com as propriedades dadas e
     * o callback de escrita (ignorado se a caracteristica nao for gravavel).
     * Deve ser chamado antes de start().
     */
    virtual void add_characteristic(const std::string& uuid, uint8_t props,
                                    BleWriteCallback on_write) = 0;

    /**
     * @brief Inicia o servico GATT e o advertising com o nome dado.
     * @return true se o advertising comecou.
     */
    virtual bool start(const std::string& device_name) = 0;

    /**
     * @brief Atualiza o valor legivel da caracteristica e notifica os clientes
     * inscritos, se houver. Retorna false se o UUID nao foi registrado.
     */
    virtual bool update_value(const std::string& uuid, const std::string& value) = 0;
};

} // namespace cafey::core
