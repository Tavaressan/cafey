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
using cafey::storage::EventQueueStore;

static void test_events_are_queued_while_offline() {
    MockNvs::reset();
    EventQueueStore store;
    store.init();
    FakeMqttClient client;
    client.connected = false;

    PendingEventPublisher pub(store, client, "dispositivos/cafey-001/eventos");
    assert(pub.enqueue(Event{100, 130, EventOrigin::BOTAO}) == ESP_OK);
    assert(pub.enqueue(Event{200, 240, EventOrigin::AGENDAMENTO}) == ESP_OK);

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
    pub.enqueue(Event{100, 130, EventOrigin::BOTAO});
    pub.enqueue(Event{200, 240, EventOrigin::AGENDAMENTO});

    client.connected = true;
    assert(pub.drain() == 2);
    assert(store.empty());
    assert(client.publishes.size() == 2);

    assert(client.publishes[0].topic == "dispositivos/cafey-001/eventos");
    assert(client.publishes[0].qos == 1);
    assert(client.publishes[0].retain == false);
    assert(client.publishes[0].payload ==
           "{\"inicio\":100,\"fim\":130,\"origem\":\"BOTAO\"}");
    assert(client.publishes[1].payload ==
           "{\"inicio\":200,\"fim\":240,\"origem\":\"AGENDAMENTO\"}");
    std::cout << "OK: test_drain_publishes_all_pending_on_reconnect_fifo\n";
}

static void test_drain_stops_on_publish_failure_and_keeps_remaining() {
    MockNvs::reset();
    EventQueueStore store;
    store.init();
    FakeMqttClient client;

    PendingEventPublisher pub(store, client, "dispositivos/cafey-001/eventos");
    pub.enqueue(Event{1, 2, EventOrigin::APP});
    pub.enqueue(Event{3, 4, EventOrigin::APP});
    pub.enqueue(Event{5, 6, EventOrigin::APP});

    client.fail_publish_after = 1; // primeira publicacao ok, segunda falha

    assert(pub.drain() == 1);
    assert(store.size() == 2); // os dois restantes ficam para a proxima tentativa

    Event front{};
    store.front(&front);
    assert(front.timestamp_inicio == 3);
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
        pub.enqueue(Event{42, 50, EventOrigin::BOTAO});
    }
    {
        EventQueueStore store;
        store.init();
        FakeMqttClient client;
        PendingEventPublisher pub(store, client, "dispositivos/cafey-001/eventos");
        assert(store.size() == 1);
        assert(pub.drain() == 1);
        assert(client.publishes[0].payload ==
               "{\"inicio\":42,\"fim\":50,\"origem\":\"BOTAO\"}");
    }
    std::cout << "OK: test_drain_survives_reboot_via_persisted_queue\n";
}

// FW-15: preparo antes do sync NTP -> horario provisorio, distinguivel na drenagem.

static void test_provisional_event_is_flagged_on_publish() {
    MockNvs::reset();
    EventQueueStore store;
    store.init();
    FakeMqttClient client;

    PendingEventPublisher pub(store, client, "dispositivos/cafey-001/eventos");
    // Preparo pelo botao antes do NTP: relogio interno ainda nao confiavel.
    pub.enqueue(cafey::storage::make_brew_event(EventOrigin::BOTAO, 5, 305, /*ntp_synced=*/false));

    assert(pub.drain() == 1);
    assert(client.publishes[0].payload ==
           "{\"inicio\":5,\"fim\":305,\"origem\":\"BOTAO\",\"relogioProvisorio\":true}");
    std::cout << "OK: test_provisional_event_is_flagged_on_publish\n";
}

static void test_synced_event_has_no_provisional_flag() {
    MockNvs::reset();
    EventQueueStore store;
    store.init();
    FakeMqttClient client;

    PendingEventPublisher pub(store, client, "dispositivos/cafey-001/eventos");
    pub.enqueue(cafey::storage::make_brew_event(EventOrigin::AGENDAMENTO, 1000, 1300, /*ntp_synced=*/true));

    assert(pub.drain() == 1);
    assert(client.publishes[0].payload ==
           "{\"inicio\":1000,\"fim\":1300,\"origem\":\"AGENDAMENTO\"}");
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
        pub.enqueue(cafey::storage::make_brew_event(EventOrigin::BOTAO, 7, 42, /*ntp_synced=*/false));
    }
    {
        EventQueueStore store;
        store.init();
        FakeMqttClient client;
        PendingEventPublisher pub(store, client, "dispositivos/cafey-001/eventos");
        assert(pub.drain() == 1);
        assert(client.publishes[0].payload ==
               "{\"inicio\":7,\"fim\":42,\"origem\":\"BOTAO\",\"relogioProvisorio\":true}");
    }
    std::cout << "OK: test_provisional_flag_survives_reboot\n";
}

int main() {
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
