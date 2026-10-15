#include <cassert>
#include <iostream>

#include "mock_esp_nvs.hpp"
#include "mock_mqtt_client.hpp"
#include "app/pending_event_publisher.hpp"
#include "storage/event.hpp"
#include "storage/event_queue_store.hpp"

using cafey::app::PendingEventPublisher;
using cafey::storage::Event;
using cafey::storage::EventOrigin;
using cafey::storage::EventResult;
using cafey::storage::EventQueueStore;

static Event make_test_event(const char* id, EventOrigin origem, EventResult resultado,
                             uint32_t duracao_s, uint32_t ts_inicio, bool provisorio) {
    Event e{};
    if (id) {
        std::strncpy(e.evento_id, id, sizeof(e.evento_id) - 1);
    }
    e.origem = origem;
    e.resultado = resultado;
    e.duracao_s = duracao_s;
    e.timestamp_inicio = ts_inicio;
    e.timestamp_fim = ts_inicio + duracao_s;
    e.horario_provisorio = provisorio;
    return e;
}

static void test_serialize_matches_backend_evento_payload() {
    // 1788267604 = 2026-09-01T13:00:04Z
    const uint32_t epoch = 1788267604;
    Event event = make_test_event("a3f10c42:7", EventOrigin::BOTAO, EventResult::CONCLUIDO,
                                  300, epoch, /*provisorio=*/false);

    const std::string json = PendingEventPublisher::serialize(event);
    const std::string expected =
        "{\"eventoId\":\"a3f10c42:7\",\"tipo\":\"PREPARO\",\"resultado\":\"CONCLUIDO\","
        "\"origem\":\"BOTAO\",\"duracaoS\":300,\"timestamp\":\"2026-09-01T13:00:04Z\"}";

    assert(json == expected);
    std::cout << "OK: test_serialize_matches_backend_evento_payload\n";
}

static void test_serialize_canceled_event_matches_backend_evento_payload() {
    const uint32_t epoch = 1788267604;
    Event event = make_test_event("a3f10c42:8", EventOrigin::APP, EventResult::CANCELADO,
                                  45, epoch, /*provisorio=*/false);

    const std::string json = PendingEventPublisher::serialize(event);
    const std::string expected =
        "{\"eventoId\":\"a3f10c42:8\",\"tipo\":\"PREPARO\",\"resultado\":\"CANCELADO\","
        "\"origem\":\"APP\",\"duracaoS\":45,\"timestamp\":\"2026-09-01T13:00:04Z\"}";

    assert(json == expected);
    std::cout << "OK: test_serialize_canceled_event_matches_backend_evento_payload\n";
}

static void test_events_are_queued_while_offline() {
    MockNvs::reset();
    EventQueueStore store;
    store.init();
    FakeMqttClient client;
    client.connected = false;

    PendingEventPublisher pub(store, client, "dispositivos/cafey-001/eventos");
    assert(pub.enqueue(make_test_event("evt:1", EventOrigin::BOTAO, EventResult::CONCLUIDO,
                                       30, 100, false)) == ESP_OK);
    assert(pub.enqueue(make_test_event("evt:2", EventOrigin::AGENDAMENTO, EventResult::CONCLUIDO,
                                       40, 200, false)) == ESP_OK);

    // Offline: drain nao publica nada e a fila permanece.
    assert(pub.drain() == 0);
    assert(client.publishes.empty());
    assert(store.size() == 2);
    std::cout << "OK: test_events_are_queued_while_offline\n";
}

static void test_drain_publishes_all_pending_on_reconnect_fifo() {
    MockNvs::reset();
    EventQueueStore store;
    store.init();
    FakeMqttClient client;
    client.connected = false;

    PendingEventPublisher pub(store, client, "dispositivos/cafey-001/eventos");
    pub.enqueue(make_test_event("evt:1", EventOrigin::BOTAO, EventResult::CONCLUIDO,
                                30, 100, false));
    pub.enqueue(make_test_event("evt:2", EventOrigin::AGENDAMENTO, EventResult::CONCLUIDO,
                                40, 200, false));

    client.connected = true;
    assert(pub.drain() == 2);
    assert(store.empty());
    assert(client.publishes.size() == 2);

    assert(client.publishes[0].topic == "dispositivos/cafey-001/eventos");
    assert(client.publishes[0].qos == 1);
    assert(client.publishes[0].retain == false);
    assert(client.publishes[0].payload == PendingEventPublisher::serialize(
               make_test_event("evt:1", EventOrigin::BOTAO, EventResult::CONCLUIDO, 30, 100, false)));
    assert(client.publishes[1].payload == PendingEventPublisher::serialize(
               make_test_event("evt:2", EventOrigin::AGENDAMENTO, EventResult::CONCLUIDO, 40, 200, false)));
    std::cout << "OK: test_drain_publishes_all_pending_on_reconnect_fifo\n";
}

static void test_drain_stops_on_publish_failure_and_keeps_remaining() {
    MockNvs::reset();
    EventQueueStore store;
    store.init();
    FakeMqttClient client;

    PendingEventPublisher pub(store, client, "dispositivos/cafey-001/eventos");
    pub.enqueue(make_test_event("evt:1", EventOrigin::APP, EventResult::CONCLUIDO, 1, 100, false));
    pub.enqueue(make_test_event("evt:2", EventOrigin::APP, EventResult::CONCLUIDO, 1, 200, false));
    pub.enqueue(make_test_event("evt:3", EventOrigin::APP, EventResult::CONCLUIDO, 1, 300, false));

    client.fail_publish_after = 1; // primeira publicacao ok, segunda falha

    assert(pub.drain() == 1);
    assert(store.size() == 2); // os dois restantes ficam para a proxima tentativa

    Event front{};
    store.front(&front);
    assert(std::string(front.evento_id) == "evt:2");
    std::cout << "OK: test_drain_stops_on_publish_failure_and_keeps_remaining\n";
}

static void test_drain_survives_reboot_via_persisted_queue() {
    MockNvs::reset();
    {
        EventQueueStore store;
        store.init();
        FakeMqttClient offline;
        offline.connected = false;
        PendingEventPublisher pub(store, offline, "dispositivos/cafey-001/eventos");
        pub.enqueue(make_test_event("boot1:42", EventOrigin::BOTAO, EventResult::CONCLUIDO, 8, 42, false));
    }
    {
        EventQueueStore store;
        store.init();
        FakeMqttClient client;
        PendingEventPublisher pub(store, client, "dispositivos/cafey-001/eventos");
        assert(store.size() == 1);
        assert(pub.drain() == 1);
        assert(client.publishes[0].payload == PendingEventPublisher::serialize(
                   make_test_event("boot1:42", EventOrigin::BOTAO, EventResult::CONCLUIDO, 8, 42, false)));
    }
    std::cout << "OK: test_drain_survives_reboot_via_persisted_queue\n";
}

// FW-15 / FW-22: preparo antes do sync NTP -> horario provisorio, distinguivel na drenagem.

static void test_provisional_event_is_flagged_on_publish() {
    MockNvs::reset();
    EventQueueStore store;
    store.init();
    FakeMqttClient client;

    PendingEventPublisher pub(store, client, "dispositivos/cafey-001/eventos");
    Event prov = make_test_event("prov:1", EventOrigin::BOTAO, EventResult::CONCLUIDO,
                                 300, 5, /*provisorio=*/true);
    pub.enqueue(prov);

    assert(pub.drain() == 1);
    const std::string payload = client.publishes[0].payload;
    assert(payload.find("\"relogioProvisorio\":true") != std::string::npos);
    assert(payload.find("\"eventoId\":\"prov:1\"") != std::string::npos);
    assert(payload.find("\"tipo\":\"PREPARO\"") != std::string::npos);
    std::cout << "OK: test_provisional_event_is_flagged_on_publish\n";
}

static void test_synced_event_has_no_provisional_flag() {
    MockNvs::reset();
    EventQueueStore store;
    store.init();
    FakeMqttClient client;

    PendingEventPublisher pub(store, client, "dispositivos/cafey-001/eventos");
    Event synced = make_test_event("sync:1", EventOrigin::AGENDAMENTO, EventResult::CONCLUIDO,
                                   300, 1000, /*provisorio=*/false);
    pub.enqueue(synced);

    assert(pub.drain() == 1);
    const std::string payload = client.publishes[0].payload;
    assert(payload.find("\"relogioProvisorio\"") == std::string::npos);
    assert(payload.find("\"eventoId\":\"sync:1\"") != std::string::npos);
    std::cout << "OK: test_synced_event_has_no_provisional_flag\n";
}

static void test_provisional_flag_survives_reboot() {
    MockNvs::reset();
    {
        EventQueueStore store;
        store.init();
        FakeMqttClient offline;
        offline.connected = false;
        PendingEventPublisher pub(store, offline, "dispositivos/cafey-001/eventos");
        pub.enqueue(make_test_event("boot1:7", EventOrigin::BOTAO, EventResult::CANCELADO,
                                    35, 7, /*provisorio=*/true));
    }
    {
        EventQueueStore store;
        store.init();
        FakeMqttClient client;
        PendingEventPublisher pub(store, client, "dispositivos/cafey-001/eventos");
        assert(pub.drain() == 1);
        const std::string payload = client.publishes[0].payload;
        assert(payload.find("\"relogioProvisorio\":true") != std::string::npos);
        assert(payload.find("\"resultado\":\"CANCELADO\"") != std::string::npos);
    }
    std::cout << "OK: test_provisional_flag_survives_reboot\n";
}

int main() {
    test_serialize_matches_backend_evento_payload();
    test_serialize_canceled_event_matches_backend_evento_payload();
    test_events_are_queued_while_offline();
    test_drain_publishes_all_pending_on_reconnect_fifo();
    test_drain_stops_on_publish_failure_and_keeps_remaining();
    test_drain_survives_reboot_via_persisted_queue();
    test_provisional_event_is_flagged_on_publish();
    test_synced_event_has_no_provisional_flag();
    test_provisional_flag_survives_reboot();
    std::cout << "Todos os testes de PendingEventPublisher passaram.\n";
    return 0;
}
