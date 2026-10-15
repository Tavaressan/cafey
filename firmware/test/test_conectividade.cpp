// Testes do AO Conectividade com MQTT anexado (FW-21): inicio da conexao no
// primeiro IP, reassinatura a cada CONNECT e publicacao de `saude`/`estado`.

#include <iostream>

#include "mock_esp_nvs.hpp"
#include "app/conectividade.hpp"
#include "core/mqtt_topics.hpp"
#include "mock_mqtt_client.hpp"
#include "storage/event_queue_store.hpp"

#define TEST_ASSERT(cond, msg)                                                  \
    do {                                                                       \
        if (!(cond)) {                                                         \
            std::cerr << "FAILED: " << msg << " (" << __FILE__ << ":"          \
                      << __LINE__ << ")" << std::endl;                         \
            return 1;                                                          \
        }                                                                      \
    } while (0)

using cafey::app::Conectividade;
using cafey::core::Message;
using cafey::core::MessageType;
using cafey::core::MqttTopics;

namespace {
const MqttTopics kTopics("3f2a9c1e-0000-4000-8000-000000000001");

void send(Conectividade& con, MessageType type, int32_t arg = 0) {
    con.post(Message{type, arg});
    con.process_pending();
}
} // namespace

int test_wifi_connected_starts_mqtt_once() {
    FakeMqttClient mqtt;
    mqtt.connected = false;
    int starts = 0;
    Conectividade con;
    con.attach_mqtt(&mqtt, &kTopics, [&] { ++starts; return true; });
    con.init();

    send(con, MessageType::WifiConnected);
    TEST_ASSERT(starts == 1, "primeiro IP inicia o MQTT");
    TEST_ASSERT(con.state() == Conectividade::State::Connecting, "aguardando CONNECT");

    send(con, MessageType::WifiDisconnected);
    send(con, MessageType::WifiConnected);
    TEST_ASSERT(starts == 1, "reconexao Wi-Fi nao reinicia o cliente (esp-mqtt reconecta)");

    std::cout << "[PASS] test_wifi_connected_starts_mqtt_once" << std::endl;
    return 0;
}

int test_mqtt_connected_subscribes_and_publishes() {
    FakeMqttClient mqtt;
    Conectividade con;
    con.attach_mqtt(&mqtt, &kTopics, [] { return true; });
    con.init();

    send(con, MessageType::MqttConnected);
    TEST_ASSERT(con.state() == Conectividade::State::Online, "CONNECT -> Online");

    TEST_ASSERT(mqtt.subscribes.size() == 2, "assina comando e agendamentos");
    TEST_ASSERT(mqtt.subscribes[0].topic == kTopics.comando, "topico exato de comando");
    TEST_ASSERT(mqtt.subscribes[1].topic == kTopics.agendamentos, "topico exato de agendamentos");
    TEST_ASSERT(mqtt.subscribes[0].qos == 1 && mqtt.subscribes[1].qos == 1, "QoS 1");

    TEST_ASSERT(mqtt.publishes.size() == 2, "publica saude e estado");
    TEST_ASSERT(mqtt.publishes[0].topic == kTopics.saude, "saude primeiro");
    TEST_ASSERT(mqtt.publishes[0].payload == "{\"online\":true}", "saude online");
    TEST_ASSERT(!mqtt.publishes[0].retain, "saude periodica sem retain");
    TEST_ASSERT(mqtt.publishes[1].topic == kTopics.estado, "estado depois");
    TEST_ASSERT(mqtt.publishes[1].payload == "{\"estado\":\"DESLIGADO\"}", "estado inicial");
    TEST_ASSERT(mqtt.publishes[1].retain, "estado retido");

    std::cout << "[PASS] test_mqtt_connected_subscribes_and_publishes" << std::endl;
    return 0;
}

int test_reconnect_resubscribes() {
    FakeMqttClient mqtt;
    Conectividade con;
    con.attach_mqtt(&mqtt, &kTopics, [] { return true; });
    con.init();

    send(con, MessageType::MqttConnected);
    send(con, MessageType::MqttDisconnected);
    TEST_ASSERT(con.state() == Conectividade::State::Connecting, "queda do broker -> Connecting");
    send(con, MessageType::MqttConnected);
    TEST_ASSERT(mqtt.subscribes.size() == 4, "sessao limpa: reassina a cada CONNECT");

    std::cout << "[PASS] test_reconnect_resubscribes" << std::endl;
    return 0;
}

int test_estado_changed_publishes_when_connected() {
    FakeMqttClient mqtt;
    Conectividade con;
    con.attach_mqtt(&mqtt, &kTopics, [] { return true; });
    con.init();

    send(con, MessageType::EstadoChanged, 1);
    TEST_ASSERT(mqtt.publishes.size() == 1, "publica a mudanca de estado");
    TEST_ASSERT(mqtt.publishes[0].topic == kTopics.estado, "no topico estado");
    TEST_ASSERT(mqtt.publishes[0].payload == "{\"estado\":\"PREPARANDO\"}", "preparando");
    TEST_ASSERT(mqtt.publishes[0].retain, "estado retido");

    send(con, MessageType::EstadoChanged, 0);
    TEST_ASSERT(mqtt.publishes.back().payload == "{\"estado\":\"DESLIGADO\"}", "desligada");

    std::cout << "[PASS] test_estado_changed_publishes_when_connected" << std::endl;
    return 0;
}

int test_estado_while_offline_goes_out_on_connect() {
    FakeMqttClient mqtt;
    mqtt.connected = false;
    Conectividade con;
    con.attach_mqtt(&mqtt, &kTopics, [] { return true; });
    con.init();

    send(con, MessageType::EstadoChanged, 1);
    TEST_ASSERT(mqtt.publishes.empty(), "sem conexao nao publica");

    mqtt.connected = true;
    send(con, MessageType::MqttConnected);
    TEST_ASSERT(mqtt.publishes.back().topic == kTopics.estado, "estado sai no CONNECT");
    TEST_ASSERT(mqtt.publishes.back().payload == "{\"estado\":\"PREPARANDO\"}",
                "com o estado corrente");

    std::cout << "[PASS] test_estado_while_offline_goes_out_on_connect" << std::endl;
    return 0;
}

int test_mqtt_connected_drains_events() {
    MockNvs::reset();
    cafey::storage::EventQueueStore queue;
    queue.init();
    queue.push(cafey::storage::make_brew_event("e1", cafey::storage::EventOrigin::BOTAO,
                                               cafey::storage::EventResult::CONCLUIDO, 30, 100, 130, true));
    queue.push(cafey::storage::make_brew_event("e2", cafey::storage::EventOrigin::APP,
                                               cafey::storage::EventResult::CONCLUIDO, 40, 200, 240, true));
    TEST_ASSERT(queue.size() == 2, "2 eventos na fila antes de conectar");

    FakeMqttClient mqtt;
    Conectividade con;
    con.attach_mqtt(&mqtt, &kTopics, [] { return true; });
    con.attach_event_queue(&queue);
    con.init();

    send(con, MessageType::MqttConnected);
    TEST_ASSERT(queue.empty(), "fila drenada no CONNECT");

    // Procura as publicacoes no topico de eventos
    int eventos_published = 0;
    for (const auto& pub : mqtt.publishes) {
        if (pub.topic == kTopics.eventos) {
            ++eventos_published;
            TEST_ASSERT(pub.qos == 1, "QoS 1 para eventos");
        }
    }
    TEST_ASSERT(eventos_published == 2, "2 eventos publicados");

    std::cout << "[PASS] test_mqtt_connected_drains_events" << std::endl;
    return 0;
}

int test_event_queued_drains_immediately_when_connected() {
    MockNvs::reset();
    cafey::storage::EventQueueStore queue;
    queue.init();

    FakeMqttClient mqtt;
    Conectividade con;
    con.attach_mqtt(&mqtt, &kTopics, [] { return true; });
    con.attach_event_queue(&queue);
    con.init();

    send(con, MessageType::MqttConnected);
    mqtt.publishes.clear();

    queue.push(cafey::storage::make_brew_event("e3", cafey::storage::EventOrigin::BOTAO,
                                               cafey::storage::EventResult::CONCLUIDO, 15, 300, 315, true));
    TEST_ASSERT(queue.size() == 1, "evento adicionado na fila");

    send(con, MessageType::EventQueued);
    TEST_ASSERT(queue.empty(), "fila drenada imediatamente via EventQueued");
    TEST_ASSERT(mqtt.publishes.size() == 1, "evento publicado");
    TEST_ASSERT(mqtt.publishes[0].topic == kTopics.eventos, "no topico eventos");

    std::cout << "[PASS] test_event_queued_drains_immediately_when_connected" << std::endl;
    return 0;
}

int test_event_queued_stays_queued_when_offline() {
    MockNvs::reset();
    cafey::storage::EventQueueStore queue;
    queue.init();

    FakeMqttClient mqtt;
    mqtt.connected = false;
    Conectividade con;
    con.attach_mqtt(&mqtt, &kTopics, [] { return true; });
    con.attach_event_queue(&queue);
    con.init();

    queue.push(cafey::storage::make_brew_event("e4", cafey::storage::EventOrigin::APP,
                                               cafey::storage::EventResult::CANCELADO, 5, 400, 405, true));
    send(con, MessageType::EventQueued);
    TEST_ASSERT(queue.size() == 1, "fila permanece intacta offline");
    TEST_ASSERT(mqtt.publishes.empty(), "nenhuma publicacao realizada offline");

    std::cout << "[PASS] test_event_queued_stays_queued_when_offline" << std::endl;
    return 0;
}

int main() {
    std::cout << "Running Cafey Conectividade (FW-21/FW-22) Unit Tests..." << std::endl;

    if (test_wifi_connected_starts_mqtt_once()) return 1;
    if (test_mqtt_connected_subscribes_and_publishes()) return 1;
    if (test_reconnect_resubscribes()) return 1;
    if (test_estado_changed_publishes_when_connected()) return 1;
    if (test_estado_while_offline_goes_out_on_connect()) return 1;
    if (test_mqtt_connected_drains_events()) return 1;
    if (test_event_queued_drains_immediately_when_connected()) return 1;
    if (test_event_queued_stays_queued_when_offline()) return 1;

    std::cout << "All Conectividade tests PASSED!" << std::endl;
    return 0;
}
