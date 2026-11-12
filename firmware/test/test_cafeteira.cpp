#include <iostream>

#include "mock_esp_nvs.hpp"
#include "app/agendador.hpp"
#include "app/cafeteira.hpp"
#include "app/conectividade.hpp"
#include "drivers/buzzer.hpp"
#include "drivers/rgb_led.hpp"
#include "storage/sound_settings_store.hpp"
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
using cafey::drivers::Buzzer;
using cafey::drivers::SoundPattern;

// Relogio manual para os testes de evento (o clock.hpp so traz o SystemClock).
namespace cafey::core {
class ManualClock : public Clock {
public:
    explicit ManualClock(std::time_t epoch) : epoch_(epoch) {}
    std::time_t now() const override { return epoch_; }
    void advance(std::time_t seconds) { epoch_ += seconds; }

private:
    std::time_t epoch_;
};
} // namespace cafey::core

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
    TEST_ASSERT(MockLedc::get_duty(LEDC_CHANNEL_0) == 0, "LED B respirando inicia apagado (duty 0)");
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
    // Simula pressao curta: nivel 0 estavel por N amostras...
    MockGpio::get_pin(GPIO_NUM_27).level = 0;
    for (int i = 0; i < 5; ++i) caf.tick();
    TEST_ASSERT(caf.pending_count() == 0, "toque curto so age na soltura");
    // ...e soltura: um unico ButtonPressed.
    MockGpio::get_pin(GPIO_NUM_27).level = 1;
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
                MockLedc::get_duty(LEDC_CHANNEL_0) == 255, "todos os canais acesos (branco)");

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
    MockNvs::reset();
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
    TEST_ASSERT(evt.timestamp_inicio == 1000, "timestamp_inicio 1000");
    TEST_ASSERT(evt.timestamp_fim == 1001, "timestamp_fim 1001");
    TEST_ASSERT(std::string(evt.evento_id).rfind("12345678:", 0) == 0, "evento_id com bootId");

    std::cout << "[PASS] test_cafeteira_enqueues_event_on_brew_finish" << std::endl;
    return 0;
}

int test_cafeteira_enqueues_event_on_brew_cancel() {
    MockGpio::reset();
    MockNvs::reset();
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
                MockLedc::get_duty(LEDC_CHANNEL_0) == 0, "Apenas pino G aceso");

    // 2. Queda de conexao com agendamento armado -> Ambar fixo (spec §5.3)
    caf.post(Message{MessageType::NetworkStatusChanged, 0});
    caf.process_pending();
    TEST_ASSERT(caf.visual_state() == CoffeeVisualState::ScheduleArmed, "continua em ScheduleArmed");
    TEST_ASSERT(caf.led().color() == LedColor::Amber, "Ambar quando agendado e offline");
    TEST_ASSERT(caf.led().pattern() == LedPattern::Solid, "Ambar deve ser FIXO");
    TEST_ASSERT(MockGpio::get_pin(GPIO_NUM_21).level == 1 &&
                MockGpio::get_pin(GPIO_NUM_22).level == 1 &&
                MockLedc::get_duty(LEDC_CHANNEL_0) == 0, "Pinos R e G acesos (ambar)");

    // 3. Remove agendamento armado mantendo offline -> Amarelo piscando (spec §5.3)
    caf.post(Message{MessageType::ScheduleArmedChanged, 0});
    caf.process_pending();
    TEST_ASSERT(caf.visual_state() == CoffeeVisualState::Idle, "retorna a Idle");
    TEST_ASSERT(caf.led().color() == LedColor::Amber, "Amarelo/Ambar");
    TEST_ASSERT(caf.led().pattern() == LedPattern::Blink, "Amarelo piscando quando sem Wi-Fi ocioso");

    std::cout << "[PASS] test_cafeteira_led_schedule_armed_states" << std::endl;
    return 0;
}

static bool six_digits(const std::string& text) {
    if (text.size() != 6) return false;
    for (char c : text) {
        if (c < '0' || c > '9') return false;
    }
    return true;
}

// Simula um toque longo no botao (nivel 0) e devolve o AO com a janela aberta.
static void long_press(TestableCafeteira& caf) {
    MockGpio::get_pin(GPIO_NUM_27).level = 0;
    const uint32_t ticks = cafey::app::Cafeteira::kLongPressMs / 10 + 10;
    for (uint32_t i = 0; i < ticks; ++i) caf.tick();
}

int test_cafeteira_long_press_opens_bind_window() {
    MockGpio::reset();
    MockGpio::get_pin(GPIO_NUM_27).level = 1;
    TestableCafeteira caf;
    int aberturas = 0;
    bool fechada = false;
    std::string codigo_callback;
    caf.set_on_bind_window_change([&](bool aberta, const std::string& codigo) {
        if (aberta) {
            ++aberturas;
            codigo_callback = codigo;
        } else {
            fechada = true;
        }
    });
    caf.boot();

    long_press(caf);

    TEST_ASSERT(caf.bind_window_open(), "toque longo abre a janela de vinculo");
    TEST_ASSERT(aberturas == 1, "callback de abertura chamado uma vez");
    TEST_ASSERT(six_digits(caf.bind_code()), "codigo tem 6 digitos");
    TEST_ASSERT(codigo_callback == caf.bind_code(), "callback recebe o mesmo codigo do BLE");
    TEST_ASSERT(caf.state() == cafey::app::Cafeteira::State::Off, "toque longo nao liga o preparo");
    TEST_ASSERT(!caf.relay_on(), "rele permanece aberto");
    TEST_ASSERT(caf.visual_state() == CoffeeVisualState::Binding, "LED indica janela de vinculo");
    TEST_ASSERT(caf.led().color() == LedColor::Blue, "janela de vinculo -> azul");
    TEST_ASSERT(caf.bind_seconds_left() > 0 &&
                    caf.bind_seconds_left() <= cafey::app::Cafeteira::kBindWindowS,
                "contagem regressiva dentro da janela");

    // Soltar depois do toque longo nao dispara o toque curto.
    MockGpio::get_pin(GPIO_NUM_27).level = 1;
    for (int i = 0; i < 5; ++i) caf.tick();
    TEST_ASSERT(caf.pending_count() == 0, "soltura apos toque longo nao liga o preparo");
    TEST_ASSERT(!fechada && caf.bind_window_open(), "janela segue aberta apos a soltura");

    std::cout << "[PASS] test_cafeteira_long_press_opens_bind_window" << std::endl;
    return 0;
}

int test_cafeteira_bind_window_expires() {
    MockGpio::reset();
    MockGpio::get_pin(GPIO_NUM_27).level = 1;
    TestableCafeteira caf;
    bool fechada = false;
    caf.set_on_bind_window_change([&](bool aberta, const std::string&) {
        if (!aberta) fechada = true;
    });
    caf.boot();

    long_press(caf);
    MockGpio::get_pin(GPIO_NUM_27).level = 1;
    for (int i = 0; i < 5; ++i) caf.tick();
    TEST_ASSERT(caf.bind_window_open(), "janela abriu");

    // Avanca os 2 min da janela (+ alguns ticks de folga).
    const uint32_t ticks = cafey::app::Cafeteira::kBindWindowS * 100 + 10;
    for (uint32_t i = 0; i < ticks; ++i) caf.tick();

    TEST_ASSERT(!caf.bind_window_open(), "janela fecha no prazo");
    TEST_ASSERT(caf.bind_code().empty(), "codigo e descartado ao fechar");
    TEST_ASSERT(fechada, "callback de fechamento chamado");
    TEST_ASSERT(caf.visual_state() == CoffeeVisualState::Idle, "LED volta ao estado ocioso");

    std::cout << "[PASS] test_cafeteira_bind_window_expires" << std::endl;
    return 0;
}

int test_cafeteira_short_press_still_toggles_brew_with_window_open() {
    MockGpio::reset();
    MockGpio::get_pin(GPIO_NUM_27).level = 1;
    TestableCafeteira caf;
    caf.boot();

    long_press(caf);
    MockGpio::get_pin(GPIO_NUM_27).level = 1;
    for (int i = 0; i < 5; ++i) caf.tick();
    TEST_ASSERT(caf.bind_window_open(), "janela abriu");

    // Toque curto durante a janela: continua ligando o preparo.
    MockGpio::get_pin(GPIO_NUM_27).level = 0;
    for (int i = 0; i < 5; ++i) caf.tick();
    MockGpio::get_pin(GPIO_NUM_27).level = 1;
    for (int i = 0; i < 5; ++i) caf.tick();
    caf.process_pending();
    TEST_ASSERT(caf.state() == cafey::app::Cafeteira::State::On, "toque curto liga o preparo");
    TEST_ASSERT(caf.bind_window_open(), "janela continua aberta durante o preparo");

    std::cout << "[PASS] test_cafeteira_short_press_still_toggles_brew_with_window_open" << std::endl;
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

// ---------------------------------------------------------------------------
// Buzzer (#311)
// ---------------------------------------------------------------------------

static void tick_n(TestableCafeteira& caf, uint32_t n) {
    for (uint32_t i = 0; i < n; ++i) caf.tick();
}

// Deixa terminar o som do boot para que o proximo evento comece do silencio.
static void let_sound_finish(TestableCafeteira& caf) {
    tick_n(caf, Buzzer::duration_ticks(caf.buzzer().pattern()) + 1);
}

int test_cafeteira_sounds_follow_events() {
    MockGpio::reset();
    MockLedc::reset();
    MockGpio::get_pin(GPIO_NUM_27).level = 1;
    TestableCafeteira caf;
    caf.boot();
    TEST_ASSERT(caf.buzzer().is_initialized(), "buzzer inicializado no on_start");
    TEST_ASSERT(caf.buzzer().pattern() == SoundPattern::Startup, "boot toca o arpejo de inicio");
    TEST_ASSERT(MockLedc::get_pin(LEDC_CHANNEL_1) == GPIO_NUM_25, "buzzer no GPIO25, canal 1");
    let_sound_finish(caf);
    TEST_ASSERT(!caf.buzzer().playing(), "som do boot termina sozinho");

    caf.post(Message{MessageType::StartBrew, 1});
    caf.process_pending();
    TEST_ASSERT(caf.buzzer().pattern() == SoundPattern::Beep, "preparo iniciado -> bipe");

    tick_n(caf, 120); // 1 s de preparo + debounce
    TEST_ASSERT(caf.last_result() == cafey::app::Cafeteira::Result::Concluido, "preparo concluido");
    TEST_ASSERT(caf.buzzer().pattern() == SoundPattern::Ready, "cafe pronto -> melodia");
    let_sound_finish(caf);

    caf.post(Message{MessageType::StartBrew, 60});
    caf.process_pending();
    let_sound_finish(caf);
    caf.post(Message{MessageType::StopBrew, 0});
    caf.process_pending();
    TEST_ASSERT(caf.buzzer().pattern() == SoundPattern::Cancel, "preparo cancelado -> descendente");
    let_sound_finish(caf);

    caf.post(Message{MessageType::StopBrew, 0});
    caf.process_pending();
    TEST_ASSERT(!caf.buzzer().playing(), "parar com a cafeteira ja desligada nao toca");

    caf.set_error(true);
    TEST_ASSERT(caf.buzzer().pattern() == SoundPattern::Error, "falha operacional -> erro");
    let_sound_finish(caf);
    caf.set_error(true);
    TEST_ASSERT(!caf.buzzer().playing(), "erro repetido nao toca de novo");
    caf.set_error(false);

    long_press(caf);
    TEST_ASSERT(caf.bind_window_open(), "janela de vinculo aberta");
    TEST_ASSERT(caf.buzzer().pattern() == SoundPattern::Binding, "janela de vinculo -> tres bipes");

    std::cout << "[PASS] test_cafeteira_sounds_follow_events" << std::endl;
    return 0;
}

int test_cafeteira_network_sound_on_edges_with_cooldown() {
    MockGpio::reset();
    MockGpio::get_pin(GPIO_NUM_27).level = 1;
    TestableCafeteira caf;
    caf.set_network_ok(false); // como no app_main, antes do start
    caf.boot();
    let_sound_finish(caf);

    caf.post(Message{MessageType::NetworkStatusChanged, 1});
    caf.process_pending();
    TEST_ASSERT(caf.buzzer().pattern() == SoundPattern::Connected, "rede conectou -> ascendente");
    let_sound_finish(caf);

    caf.post(Message{MessageType::NetworkStatusChanged, 1});
    caf.process_pending();
    TEST_ASSERT(!caf.buzzer().playing(), "mesmo estado nao toca");

    caf.post(Message{MessageType::NetworkStatusChanged, 0});
    caf.process_pending();
    TEST_ASSERT(!caf.buzzer().playing(), "queda logo apos conectar fica muda (intervalo minimo)");
    TEST_ASSERT(caf.led().color() == LedColor::Amber, "LED continua refletindo a rede");

    caf.post(Message{MessageType::NetworkStatusChanged, 1});
    caf.process_pending();
    tick_n(caf, cafey::app::Cafeteira::kNetworkSoundCooldownS * 100);
    caf.post(Message{MessageType::NetworkStatusChanged, 0});
    caf.process_pending();
    TEST_ASSERT(caf.buzzer().pattern() == SoundPattern::Disconnected,
                "queda apos o intervalo -> descendente");

    std::cout << "[PASS] test_cafeteira_network_sound_on_edges_with_cooldown" << std::endl;
    return 0;
}

int test_cafeteira_sound_never_delays_command_button_or_led() {
    MockGpio::reset();
    MockLedc::reset();
    MockGpio::get_pin(GPIO_NUM_27).level = 1;
    TestableCafeteira caf;
    caf.boot();

    // Melodia mais longa tocando: o comando de preparo age no mesmo dispatch.
    caf.post(Message{MessageType::BrewFinished, 0});
    caf.post(Message{MessageType::StartBrew, 60});
    caf.process_pending();
    TEST_ASSERT(caf.relay_on(), "rele fecha no mesmo dispatch, com som tocando");
    TEST_ASSERT(caf.buzzer().playing(), "som segue em paralelo");
    TEST_ASSERT(caf.led().color() == LedColor::Green, "LED muda na hora");

    // LED segue animando (verde pulsando: alterna a cada 25 ticks) enquanto toca.
    const int g_before = MockGpio::get_pin(GPIO_NUM_22).level;
    tick_n(caf, 25);
    TEST_ASSERT(MockGpio::get_pin(GPIO_NUM_22).level != g_before, "pulso do LED avanca no tick");

    // Botao: debounce de 3 amostras continua valendo com som tocando.
    caf.set_error(true); // erro: padrao mais longo, prioridade maxima
    TEST_ASSERT(caf.buzzer().pattern() == SoundPattern::Error, "erro tocando");
    MockGpio::get_pin(GPIO_NUM_27).level = 0;
    tick_n(caf, 5);
    MockGpio::get_pin(GPIO_NUM_27).level = 1;
    tick_n(caf, 5);
    TEST_ASSERT(caf.pending_count() == 1, "toque curto enfileirado em 10 ticks, como sem som");
    caf.process_pending();
    TEST_ASSERT(!caf.relay_on(), "toque cancela o preparo na hora");

    std::cout << "[PASS] test_cafeteira_sound_never_delays_command_button_or_led" << std::endl;
    return 0;
}

int test_cafeteira_mute_persists_across_reboot() {
    MockGpio::reset();
    MockNvs::reset();
    MockGpio::get_pin(GPIO_NUM_27).level = 1;
    {
        cafey::storage::SoundSettingsStore settings;
        settings.init();
        TestableCafeteira caf;
        caf.attach_sound_settings(&settings);
        caf.boot();
        TEST_ASSERT(caf.buzzer().pattern() == SoundPattern::Startup, "som ligado por padrao");

        caf.post(Message{MessageType::SetMuted, 1});
        caf.process_pending();
        TEST_ASSERT(caf.buzzer().muted() && !caf.buzzer().playing(), "silenciar corta o som");
        TEST_ASSERT(settings.muted(), "preferencia gravada");
        caf.post(Message{MessageType::StartBrew, 60});
        caf.process_pending();
        TEST_ASSERT(caf.relay_on() && !caf.buzzer().playing(), "silenciado: preparo sem bipe");
    }

    // "Reboot": objetos novos lendo o mesmo NVS.
    cafey::storage::SoundSettingsStore settings;
    settings.init();
    TestableCafeteira caf;
    caf.attach_sound_settings(&settings);
    caf.boot();
    TEST_ASSERT(caf.buzzer().muted(), "silencioso sobrevive a reboot");
    TEST_ASSERT(!caf.buzzer().playing(), "boot silencioso");

    caf.post(Message{MessageType::SetMuted, 0});
    caf.process_pending();
    TEST_ASSERT(!caf.buzzer().muted() && !settings.muted(), "som reativado e gravado");
    TEST_ASSERT(caf.buzzer().pattern() == SoundPattern::DoubleBeep, "confirma com dois bipes");

    std::cout << "[PASS] test_cafeteira_mute_persists_across_reboot" << std::endl;
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
    if (test_cafeteira_long_press_opens_bind_window()) return 1;
    if (test_cafeteira_bind_window_expires()) return 1;
    if (test_cafeteira_short_press_still_toggles_brew_with_window_open()) return 1;
    if (test_cafeteira_sounds_follow_events()) return 1;
    if (test_cafeteira_network_sound_on_edges_with_cooldown()) return 1;
    if (test_cafeteira_sound_never_delays_command_button_or_led()) return 1;
    if (test_cafeteira_mute_persists_across_reboot()) return 1;
    if (test_skeleton_active_objects()) return 1;
    std::cout << "All Cafeteira AO tests PASSED!" << std::endl;
    return 0;
}
