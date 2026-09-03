#include <iostream>

#include "app/agendador.hpp"
#include "app/cafeteira.hpp"
#include "app/conectividade.hpp"

#define TEST_ASSERT(cond, msg)                                                  \
    do {                                                                       \
        if (!(cond)) {                                                         \
            std::cerr << "FAILED: " << msg << " (" << __FILE__ << ":"          \
                      << __LINE__ << ")" << std::endl;                         \
            return 1;                                                          \
        }                                                                      \
    } while (0)

using cafey::core::Message;
using cafey::core::MessageType;

// Expoe os hooks protegidos para acionar o AO de forma sincrona no teste.
class TestableCafeteira : public cafey::app::Cafeteira {
public:
    using Cafeteira::Cafeteira;
    void boot() { init(); on_start(); }
    void tick() { on_tick(); }
};

int test_cafeteira_button_toggles_relay_and_led() {
    MockGpio::reset();
    TestableCafeteira caf;
    caf.boot();

    // Estado inicial: desligada, rele aberto, LED vermelho.
    TEST_ASSERT(caf.state() == cafey::app::Cafeteira::State::Off, "inicia desligada");
    TEST_ASSERT(!caf.relay_on(), "rele inicia aberto");
    TEST_ASSERT(MockGpio::get_pin(GPIO_NUM_21).level == 1, "LED R aceso");
    TEST_ASSERT(MockGpio::get_pin(GPIO_NUM_22).level == 0, "LED G apagado");

    // Clique no botao -> liga.
    caf.post(Message{MessageType::ButtonPressed, 0});
    caf.process_pending();
    TEST_ASSERT(caf.state() == cafey::app::Cafeteira::State::On, "liga apos clique");
    TEST_ASSERT(caf.relay_on(), "rele fecha");
    TEST_ASSERT(MockGpio::get_pin(GPIO_NUM_26).level == 1, "GPIO rele HIGH");
    TEST_ASSERT(MockGpio::get_pin(GPIO_NUM_22).level == 1, "LED G aceso (verde)");
    TEST_ASSERT(MockGpio::get_pin(GPIO_NUM_21).level == 0, "LED R apagado");

    // Outro clique -> desliga.
    caf.post(Message{MessageType::ButtonPressed, 0});
    caf.process_pending();
    TEST_ASSERT(caf.state() == cafey::app::Cafeteira::State::Off, "desliga apos 2o clique");
    TEST_ASSERT(!caf.relay_on(), "rele reabre");
    TEST_ASSERT(MockGpio::get_pin(GPIO_NUM_26).level == 0, "GPIO rele LOW");

    std::cout << "[PASS] test_cafeteira_button_toggles_relay_and_led" << std::endl;
    return 0;
}

int test_cafeteira_debounce_polling_posts_event() {
    MockGpio::reset();
    // Botao solto = 1 (pull-up externo); o mock nao simula pull-up.
    MockGpio::get_pin(GPIO_NUM_27).level = 1;
    TestableCafeteira caf;
    caf.boot();
    // Simula pressao: nivel 0 estavel por N amostras.
    MockGpio::get_pin(GPIO_NUM_27).level = 0;
    for (int i = 0; i < 5; ++i) caf.tick();
    TEST_ASSERT(caf.pending_count() == 1, "debounce deve enfileirar exatamente 1 ButtonPressed");
    caf.process_pending();
    TEST_ASSERT(caf.state() == cafey::app::Cafeteira::State::On, "evento do polling liga a cafeteira");

    std::cout << "[PASS] test_cafeteira_debounce_polling_posts_event" << std::endl;
    return 0;
}

int test_skeleton_active_objects() {
    cafey::app::Conectividade con;
    con.start();
    con.post(Message{MessageType::WifiConnected, 0});
    con.process_pending();
    TEST_ASSERT(con.state() == cafey::app::Conectividade::State::Online, "Conectividade -> Online");
    con.post(Message{MessageType::WifiDisconnected, 0});
    con.process_pending();
    TEST_ASSERT(con.state() == cafey::app::Conectividade::State::Offline, "Conectividade -> Offline");

    cafey::app::Agendador age;
    age.start();
    age.post(Message{MessageType::TimeSynced, 0});
    age.process_pending();
    TEST_ASSERT(age.state() == cafey::app::Agendador::State::Synced, "Agendador -> Synced");

    std::cout << "[PASS] test_skeleton_active_objects" << std::endl;
    return 0;
}

int main() {
    std::cout << "Running Cafey Cafeteira AO Unit Tests..." << std::endl;
    if (test_cafeteira_button_toggles_relay_and_led()) return 1;
    if (test_cafeteira_debounce_polling_posts_event()) return 1;
    if (test_skeleton_active_objects()) return 1;
    std::cout << "All Cafeteira AO tests PASSED!" << std::endl;
    return 0;
}
