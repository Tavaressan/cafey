#pragma once

#include <cstddef>
#include <cstdint>

#include "freertos_compat.hpp"
#include "message.hpp"

namespace cafey::core {

/**
 * @brief Classe base do padrao Active Object sobre FreeRTOS (spec 5.2).
 *
 * Cada AO encapsula uma task FreeRTOS + uma fila de mensagens propria e uma
 * maquina de estados interna. A unica forma de interacao entre AOs e post().
 * Sem memoria compartilhada -> sem mutexes por construcao.
 */
class ActiveObject {
public:
    ActiveObject(const char* name, uint32_t queue_length,
                 uint32_t stack_size = 4096, uint32_t priority = 5,
                 uint32_t tick_period_ms = 0);
    virtual ~ActiveObject();

    ActiveObject(const ActiveObject&) = delete;
    ActiveObject& operator=(const ActiveObject&) = delete;

    /** Cria a fila (idempotente). */
    bool init();

    /** Cria a fila (se preciso) e a task de despacho. */
    bool start();

    /** Sinaliza o laco de despacho para terminar. */
    void stop();

    /** Enfileira uma mensagem sem bloquear. false se a fila estiver cheia. */
    bool post(const Message& msg);

    /**
     * Drena e despacha todas as mensagens pendentes sem bloquear.
     * Util para acionar o AO de forma sincrona (testes de host).
     */
    bool process_pending();

    [[nodiscard]] bool is_running() const noexcept { return running_; }
    [[nodiscard]] const char* name() const noexcept { return name_; }
    [[nodiscard]] std::size_t pending_count() const;

protected:
    /** Trata uma mensagem. Implementa a maquina de estados do AO concreto. */
    virtual void dispatch(const Message& msg) = 0;

    /** Chamado uma vez quando a task inicia. */
    virtual void on_start() {}

    /** Chamado quando o receive expira sem mensagem (polling periodico). */
    virtual void on_tick() {}

private:
    static void task_entry(void* self);
    void run();

    const char* name_;
    uint32_t queue_length_;
    uint32_t stack_size_;
    uint32_t priority_;
    TickType_t tick_ticks_;
    QueueHandle_t queue_ = nullptr;
    TaskHandle_t task_ = nullptr;
    volatile bool running_ = false;
};

} // namespace cafey::core
