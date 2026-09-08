#pragma once

#include <sys/time.h>

#include <functional>

namespace cafey::core {

/**
 * @brief Sincronizacao do relogio interno via SNTP do ESP-IDF (spec §5.5).
 *
 * Apos start(), o SNTP roda em background e atualiza o relogio do sistema.
 * O callback informado e invocado a cada notificacao de horario recebida do
 * servidor, permitindo ao Agendador passar ao estado sincronizado.
 */
class NtpSync {
public:
    explicit NtpSync(std::function<void()> on_synced = nullptr);

    /** Configura e inicia o cliente SNTP (modo poll, pool.ntp.org). */
    void start();

private:
    static void on_sync_notification(struct timeval* tv);

    static std::function<void()> s_on_synced;
};

} // namespace cafey::core
