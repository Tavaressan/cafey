// Testes da politica de rodada de Wi-Fi: so sinaliza a falha depois de tentar
// todas as redes conhecidas.

#include <iostream>

#include "core/wifi_round_tracker.hpp"

#define TEST_ASSERT(cond, msg)                                                  \
    do {                                                                       \
        if (!(cond)) {                                                         \
            std::cerr << "FAILED: " << msg << " (" << __FILE__ << ":"          \
                      << __LINE__ << ")" << std::endl;                         \
            return 1;                                                          \
        }                                                                      \
    } while (0)

using cafey::core::WifiRoundTracker;

int test_single_network_signals_on_first_failure() {
    WifiRoundTracker t;
    auto o = t.on_failure(1);
    TEST_ASSERT(o.signal && o.round_done, "com uma rede so, a primeira falha encerra a rodada e sinaliza");
    return 0;
}

int test_signals_only_after_all_networks_failed() {
    WifiRoundTracker t;
    auto o = t.on_failure(3);
    TEST_ASSERT(!o.signal && !o.round_done, "1a de 3 falhas: nada a sinalizar, tenta a proxima");
    o = t.on_failure(3);
    TEST_ASSERT(!o.signal && !o.round_done, "2a de 3 falhas: ainda nao");
    o = t.on_failure(3);
    TEST_ASSERT(o.signal && o.round_done, "3a de 3 falhas: sinaliza e aplica backoff");
    return 0;
}

int test_signals_once_per_outage() {
    WifiRoundTracker t;
    for (int i = 0; i < 2; ++i) t.on_failure(2);
    TEST_ASSERT(t.signaled(), "sinalizou na 1a rodada");
    t.on_failure(2);
    auto o = t.on_failure(2);
    TEST_ASSERT(o.round_done && !o.signal, "2a rodada termina com backoff, sem repetir o aviso");
    return 0;
}

int test_connection_resets_the_round_and_rearms_signal() {
    WifiRoundTracker t;
    t.on_failure(3);
    t.on_connected();
    TEST_ASSERT(t.failures_in_round() == 0 && !t.signaled(), "conectar zera a rodada");
    t.on_failure(3);
    t.on_failure(3);
    auto o = t.on_failure(3);
    TEST_ASSERT(o.signal, "nova queda sinaliza de novo apos tentar todas");
    return 0;
}

int test_no_known_networks_counts_as_one() {
    WifiRoundTracker t;
    TEST_ASSERT(t.on_failure(0).round_done, "lista vazia nao trava a rodada");
    return 0;
}

int test_list_shrinking_mid_round_still_closes() {
    WifiRoundTracker t;
    t.on_failure(3);
    t.on_failure(3);
    auto o = t.on_failure(2); // uma rede foi esquecida no meio da rodada
    TEST_ASSERT(o.round_done && o.signal, "rodada fecha com a lista menor");
    return 0;
}

int main() {
    std::cout << "Running Cafey WifiRoundTracker Unit Tests..." << std::endl;
    int tests = 0;
    if (test_single_network_signals_on_first_failure()) return 1; ++tests;
    if (test_signals_only_after_all_networks_failed()) return 1; ++tests;
    if (test_signals_once_per_outage()) return 1; ++tests;
    if (test_connection_resets_the_round_and_rearms_signal()) return 1; ++tests;
    if (test_no_known_networks_counts_as_one()) return 1; ++tests;
    if (test_list_shrinking_mid_round_still_closes()) return 1; ++tests;
    std::cout << "All " << tests << " WifiRoundTracker tests PASSED!" << std::endl;
    return 0;
}
