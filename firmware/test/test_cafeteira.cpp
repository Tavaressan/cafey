#include <iostream>

#include "mock_esp_nvs.hpp"
#include "app/agendador.hpp"
#include "app/cafeteira.hpp"
#include "app/conectividade.hpp"
#include "drivers/rgb_led.hpp"
#include "storage/event_queue_store.hpp"
#include "storage/event_id_generator.hpp"

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
using cafey::drivers::CoffeeVisualState;
using cafey::drivers::LedColor;
using cafey::drivers::LedPattern;

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

    // Estado inicial: desligada, rele aberto, LED ocioso conectado (Azul respirando, spec §5.3).
    TEST_ASSERT(caf.state() == cafey::app::Cafeteira::State::Off, "inicia desligada");
    TEST_ASSERT(!caf.relay_on(), "rele inicia aberto");
    TEST_ASSERT(caf.visual_state() == CoffeeVisualState::Idle, "inicia em Idle");
    TEST_ASSERT(caf.led().color() == LedColor::Blue, "LED Azul quando conectado e ocioso");
    TEST_ASSERT(MockGpio::get_pin(GPIO_NUM_23).level == 1, "LED B aceso");
    TEST_ASSERT(MockGpio::get_pin(GPIO_NUM_21).level == 0, "LED R apagado");

    // Clique no botao -> liga (preparo: Verde pulsando).
    caf.post(Message{MessageType::ButtonPressed, 0});
    caf.process_pending();
    TEST_ASSERT(caf.state() == cafey::app::Cafeteira::State::On, "liga apos clique");
    TEST_ASSERT(caf.relay_on(), "rele fecha");
    TEST_ASSERT(MockGpio::get_pin(GPIO_NUM_26).level == 0, "GPIO rele LOW (active-low ligado)");
    TEST_ASSERT(caf.visual_state() == CoffeeVisualState::Brewing, "visual state Brewing");
    TEST_ASSERT(caf.led().color() == LedColor::Green, "LED verde durante preparo");
    TEST_ASSERT(MockGpio::get_pin(GPIO_NUM_22).level == 1, "LED G aceso");
    TEST_ASSERT(MockGpio::get_pin(GPIO_NUM_21).level == 0, "LED R apagado");

    // Outro clique -> cancela preparo / desliga (retorna para Idle / Azul).
    caf.post(Message{MessageType::ButtonPressed, 0});
    caf.process_pending();
    TEST_ASSERT(caf.state() == cafey::app::Cafeteira::State::Off, "desliga apos 2o clique");
    TEST_ASSERT(!caf.relay_on(), "rele reabre");
    TEST_ASSERT(MockGpio::get_pin(GPIO_NUM_26).level == 1, "GPIO rele HIGH (active-low desligado)");
    TEST_ASSERT(caf.visual_state() == CoffeeVisualState::Idle, "retorna para Idle");
    TEST_ASSERT(caf.led().color() == LedColor::Blue, "LED volta a ser Azul");

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

int test_cafeteira_brew_timer_completes_with_concluido() {
    MockGpio::reset();
    MockGpio::get_pin(GPIO_NUM_27).level = 1; // botao solto
    TestableCafeteira caf;
    caf.boot();

    // Comando de preparo com duracaoS = 2 s.
    caf.post(Message{MessageType::StartBrew, 2});
    caf.process_pending();
    TEST_ASSERT(caf.state() == cafey::app::Cafeteira::State::On, "preparo iniciado");
    TEST_ASSERT(caf.relay_on(), "rele fecha durante o preparo");
    TEST_ASSERT(caf.brew_seconds_left() == 2, "temporizador armado com 2 s");

    // 2 s = 200 ticks de 10 ms (alguns ticks iniciais sao gastos estabilizando
    // o debounce do botao antes de o temporizador comecar a contar).
    for (int i = 0; i < 150; ++i) caf.tick();
    TEST_ASSERT(caf.state() == cafey::app::Cafeteira::State::On, "ainda preparando antes do fim");
    for (int i = 0; i < 100; ++i) caf.tick();
    TEST_ASSERT(caf.state() == cafey::app::Cafeteira::State::Off, "temporizador encerra o preparo");
    TEST_ASSERT(!caf.relay_on(), "rele abre ao fim do preparo");
    TEST_ASSERT(caf.last_result() == cafey::app::Cafeteira::Result::Concluido,
                "resultado CONCLUIDO ao fim do temporizador");

    // Conclusao normal -> estado visual Ready (Branco fixo, spec §5.3)
    TEST_ASSERT(caf.visual_state() == CoffeeVisualState::Ready, "estado visual Ready");
    TEST_ASSERT(caf.led().color() == LedColor::White, "LED Branco quando cafe pronto");
    TEST_ASSERT(MockGpio::get_pin(GPIO_NUM_21).level == 1 &&
                MockGpio::get_pin(GPIO_NUM_22).level == 1 &&
                MockGpio::get_pin(GPIO_NUM_23).level == 1, "todos os pinos acesos (branco)");

    std::cout << "[PASS] test_cafeteira_brew_timer_completes_with_concluido" << std::endl;
    return 0;
}

int test_cafeteira_button_cancels_brew() {
    MockGpio::reset();
    MockGpio::get_pin(GPIO_NUM_27).level = 1;
    TestableCafeteira caf;
    caf.boot();

    caf.post(Message{MessageType::StartBrew, 60});
    caf.process_pending();
    TEST_ASSERT(caf.state() == cafey::app::Cafeteira::State::On, "preparo iniciado");

    // Toque durante preparo cancela (UC-09).
    caf.post(Message{MessageType::ButtonPressed, 0});
    caf.process_pending();
    TEST_ASSERT(caf.state() == cafey::app::Cafeteira::State::Off, "preparo cancelado");
    TEST_ASSERT(!caf.relay_on(), "rele reabre");
    TEST_ASSERT(caf.last_result() == cafey::app::Cafeteira::Result::Cancelado,
                "resultado CANCELADO");
    TEST_ASSERT(caf.visual_state() == CoffeeVisualState::Idle, "cancelamento retorna a Idle (nao Ready)");
    TEST_ASSERT(caf.led().color() == LedColor::Blue, "LED volta a ser Azul");

    std::cout << "[PASS] test_cafeteira_button_cancels_brew" << std::endl;
    return 0;
}

int test_cafeteira_start_brew_without_duration_uses_default() {
    MockGpio::reset();
    TestableCafeteira caf;
    caf.boot();

    // duracaoS = 0 cai no default (kDefaultDurationS = 300 s).
    caf.post(Message{MessageType::StartBrew, 0});
    caf.process_pending();
    TEST_ASSERT(caf.state() == cafey::app::Cafeteira::State::On, "preparo iniciado");
    TEST_ASSERT(caf.brew_seconds_left() == cafey::app::Cafeteira::kDefaultDurationS,
                "duracao default aplicada");

    std::cout << "[PASS] test_cafeteira_start_brew_without_duration_uses_default" << std::endl;
    return 0;
}

int test_cafeteira_start_brew_clamps_duration_above_max() {
    MockGpio::reset();
    TestableCafeteira caf;
    caf.boot();

    // duracaoS = 1200 s (> 900 s) e clampada no teto de seguranca local.
    caf.post(Message{MessageType::StartBrew, 1200});
    caf.process_pending();
    TEST_ASSERT(caf.state() == cafey::app::Cafeteira::State::On, "preparo iniciado");
    TEST_ASSERT(caf.brew_seconds_left() == cafey::app::Cafeteira::kMaxDurationS,
                "duracao clampada para 900 s");

    std::cout << "[PASS] test_cafeteira_start_brew_clamps_duration_above_max" << std::endl;
    return 0;
}

int test_cafeteira_start_brew_boundary_at_max_duration() {
    MockGpio::reset();
    TestableCafeteira caf;
    caf.boot();

    // duracaoS = 900 s (exatamente o teto) passa sem clamp.
    caf.post(Message{MessageType::StartBrew, 900});
    caf.process_pending();
    TEST_ASSERT(caf.brew_seconds_left() == 900, "900 s mantido");

    std::cout << "[PASS] test_cafeteira_start_brew_boundary_at_max_duration" << std::endl;
    return 0;
}

int test_cafeteira_enqueues_event_on_brew_finish() {
    MockGpio::reset();
    cafey::storage::EventQueueStore queue;
    TEST_ASSERT(queue.init() == ESP_OK, "queue.init");

    cafey::storage::EventIdGenerator id_gen;
    TEST_ASSERT(id_gen.init(0x12345678) == ESP_OK, "id_gen.init");

    cafey::core::ManualClock manual_clock(1000);

    TestableCafeteira caf;
    caf.attach_event_queue(&queue, &id_gen, &manual_clock);
    caf.boot();

    // Inicia preparo de 1 segundo (100 ticks)
    caf.post(Message{MessageType::StartBrew, 1,
                     static_cast<int32_t>(cafey::storage::EventOrigin::APP)});
    caf.process_pending();

    // Avanca 1 segundo no relogio e roda os 100 ticks
    manual_clock.advance(1);
    for (int i = 0; i < 110; ++i) caf.tick();

    TEST_ASSERT(caf.state() == cafey::app::Cafeteira::State::Off, "preparo finalizado");
    TEST_ASSERT(queue.size() == 1, "evento enfileirado");

    cafey::storage::Event evt{};
    TEST_ASSERT(queue.front(&evt) == ESP_OK, "leitura do evento");
    TEST_ASSERT(evt.resultado == cafey::storage::EventResult::CONCLUIDO, "resultado CONCLUIDO");
    TEST_ASSERT(evt.origem == cafey::storage::EventOrigin::APP, "origem APP");
    TEST_ASSERT(evt.duracao_s == 1, "duracao_s 1s");
    TEST_ASSERT(evt.inicio_epoch == 1000, "inicio_epoch 1000");
    TEST_ASSERT(evt.fim_epoch == 1001, "fim_epoch 1001");
    TEST_ASSERT(std::string(evt.evento_id).rfind("12345678:", 0) == 0, "evento_id com bootId");

    std::cout << "[PASS] test_cafeteira_enqueues_event_on_brew_finish" << std::endl;
    return 0;
}

int test_cafeteira_enqueues_event_on_brew_cancel() {
    MockGpio::reset();
    cafey::storage::EventQueueStore queue;
    TEST_ASSERT(queue.init() == ESP_OK, "queue.init");

    cafey::storage::EventIdGenerator id_gen;
    TEST_ASSERT(id_gen.init(0xabcdef01) == ESP_OK, "id_gen.init");

    cafey::core::ManualClock manual_clock(2000);

    TestableCafeteira caf;
    caf.attach_event_queue(&queue, &id_gen, &manual_clock);
    caf.boot();

    // Inicia preparo pelo botao
    caf.post(Message{MessageType::ButtonPressed, 0});
    caf.process_pending();
    TEST_ASSERT(caf.state() == cafey::app::Cafeteira::State::On, "preparo iniciado pelo botao");

    // Corre 50 ticks (0.5 s)
    for (int i = 0; i < 50; ++i) caf.tick();

    // Cancela pelo botao
    caf.post(Message{MessageType::ButtonPressed, 0});
    caf.process_pending();
    TEST_ASSERT(caf.state() == cafey::app::Cafeteira::State::Off, "preparo cancelado");
    TEST_ASSERT(queue.size() == 1, "evento de cancelamento enfileirado");

    cafey::storage::Event evt{};
    TEST_ASSERT(queue.front(&evt) == ESP_OK, "leitura do evento cancelado");
    TEST_ASSERT(evt.resultado == cafey::storage::EventResult::CANCELADO, "resultado CANCELADO");
    TEST_ASSERT(evt.origem == cafey::storage::EventOrigin::BOTAO, "origem BOTAO");
    TEST_ASSERT(std::string(evt.evento_id).rfind("abcdef01:", 0) == 0, "evento_id com bootId");

    std::cout << "[PASS] test_cafeteira_enqueues_event_on_brew_cancel" << std::endl;
    return 0;
}

int test_cafeteira_led_schedule_armed_states() {
    MockGpio::reset();
    TestableCafeteira caf;
    caf.boot();

    // 1. Notifica agendamento armado com rede OK -> Verde fixo (spec §5.3)
    caf.post(Message{MessageType::ScheduleArmedChanged, 1});
    caf.process_pending();
    TEST_ASSERT(caf.visual_state() == CoffeeVisualState::ScheduleArmed, "visual state ScheduleArmed");
    TEST_ASSERT(caf.led().color() == LedColor::Green, "Verde quando agendado e online");
    TEST_ASSERT(caf.led().pattern() == LedPattern::Solid, "Fixo");
    TEST_ASSERT(MockGpio::get_pin(GPIO_NUM_22).level == 1 &&
                MockGpio::get_pin(GPIO_NUM_21).level == 0 &&
                MockGpio::get_pin(GPIO_NUM_23).level == 0, "Apenas pino G aceso");

    // 2. Queda de conexao com agendamento armado -> Ambar fixo (spec §5.3)
    caf.post(Message{MessageType::NetworkStatusChanged, 0});
    caf.process_pending();
    TEST_ASSERT(caf.visual_state() == CoffeeVisualState::ScheduleArmed, "continua em ScheduleArmed");
    TEST_ASSERT(caf.led().color() == LedColor::Amber, "Ambar quando agendado e offline");
    TEST_ASSERT(caf.led().pattern() == LedPattern::Solid, "Ambar deve ser FIXO");
    TEST_ASSERT(MockGpio::get_pin(GPIO_NUM_21).level == 1 &&
                MockGpio::get_pin(GPIO_NUM_22).level == 1 &&
                MockGpio::get_pin(GPIO_NUM_23).level == 0, "Pinos R e G acesos (ambar)");

    // 3. Remove agendamento armado mantendo offline -> Amarelo piscando (spec §5.3)
    caf.post(Message{MessageType::ScheduleArmedChanged, 0});
    caf.process_pending();
    TEST_ASSERT(caf.visual_state() == CoffeeVisualState::Idle, "retorna a Idle");
    TEST_ASSERT(caf.led().color() == LedColor::Amber, "Amarelo/Ambar");
    TEST_ASSERT(caf.led().pattern() == LedPattern::Blink, "Amarelo piscando quando sem Wi-Fi ocioso");

    std::cout << "[PASS] test_cafeteira_led_schedule_armed_states" << std::endl;
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
    std::cout << "Running Cafey Cafeteira AO Unit Tests (Spec §5.3)..." << std::endl;
    if (test_cafeteira_button_toggles_relay_and_led()) return 1;
    if (test_cafeteira_debounce_polling_posts_event()) return 1;
    if (test_cafeteira_brew_timer_completes_with_concluido()) return 1;
    if (test_cafeteira_button_cancels_brew()) return 1;
    if (test_cafeteira_start_brew_without_duration_uses_default()) return 1;
    if (test_cafeteira_start_brew_clamps_duration_above_max()) return 1;
    if (test_cafeteira_start_brew_boundary_at_max_duration()) return 1;
    if (test_cafeteira_enqueues_event_on_brew_finish()) return 1;
    if (test_cafeteira_enqueues_event_on_brew_cancel()) return 1;
    if (test_cafeteira_led_schedule_armed_states()) return 1;
    if (test_skeleton_active_objects()) return 1;
    std::cout << "All Cafeteira AO tests PASSED!" << std::endl;
    return 0;
}
