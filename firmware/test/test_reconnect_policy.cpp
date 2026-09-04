#include <cassert>
#include <iostream>
#include "reconnect_policy.hpp"

using cafey::core::ReconnectPolicy;

static void test_first_delay_is_base_delay() {
    ReconnectPolicy policy;
    assert(policy.next_delay_ms() == ReconnectPolicy::kBaseDelayMs);
    assert(policy.attempts() == 1);
    std::cout << "OK: test_first_delay_is_base_delay\n";
}

static void test_delay_doubles_each_attempt() {
    ReconnectPolicy policy;
    assert(policy.next_delay_ms() == 1000);
    assert(policy.next_delay_ms() == 2000);
    assert(policy.next_delay_ms() == 4000);
    assert(policy.next_delay_ms() == 8000);
    assert(policy.attempts() == 4);
    std::cout << "OK: test_delay_doubles_each_attempt\n";
}

static void test_delay_caps_at_max() {
    ReconnectPolicy policy;
    uint32_t delay = 0;
    for (int i = 0; i < 20; ++i) {
        delay = policy.next_delay_ms();
    }
    assert(delay == ReconnectPolicy::kMaxDelayMs);
    std::cout << "OK: test_delay_caps_at_max\n";
}

static void test_reset_restores_base_delay_and_attempts() {
    ReconnectPolicy policy;
    policy.next_delay_ms();
    policy.next_delay_ms();
    policy.next_delay_ms();
    policy.reset();
    assert(policy.attempts() == 0);
    assert(policy.next_delay_ms() == ReconnectPolicy::kBaseDelayMs);
    std::cout << "OK: test_reset_restores_base_delay_and_attempts\n";
}

int main() {
    test_first_delay_is_base_delay();
    test_delay_doubles_each_attempt();
    test_delay_caps_at_max();
    test_reset_restores_base_delay_and_attempts();
    std::cout << "Todos os testes de ReconnectPolicy passaram.\n";
    return 0;
}
