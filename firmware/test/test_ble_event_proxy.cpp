#include <cassert>
#include <iostream>
#include <string>

#include "app/ble_event_proxy.hpp"
#include "mock_ble_server.hpp"
#include "mock_esp_nvs.hpp"
#include "storage/event.hpp"
#include "storage/event_queue_store.hpp"

using cafey::app::BleEventProxy;
using cafey::storage::Event;
using cafey::storage::EventOrigin;
using cafey::storage::EventQueueStore;

static Event make_event(uint32_t inicio, uint32_t fim, EventOrigin origem) {
    Event e{};
    e.timestamp_inicio = inicio;
    e.timestamp_fim = fim;
    e.origem = origem;
    return e;
}

static void test_publishes_pending_queue_as_json_array() {
    MockNvs::reset();
    EventQueueStore queue;
    assert(queue.init() == ESP_OK);
    assert(queue.push(make_event(100, 200, EventOrigin::BOTAO)) == ESP_OK);
    assert(queue.push(make_event(300, 400, EventOrigin::APP)) == ESP_OK);

    FakeBleServer server;
    BleEventProxy proxy(server, queue);
    proxy.begin();

    const std::string json = server.read(BleEventProxy::kPendingUuid);
    assert(json.find("\"inicio\":100") != std::string::npos);
    assert(json.find("\"inicio\":300") != std::string::npos);
    assert(json.front() == '[' && json.back() == ']');
    std::cout << "OK: test_publishes_pending_queue_as_json_array\n";
}

static void test_confirm_removes_only_confirmed_subset() {
    MockNvs::reset();
    EventQueueStore queue;
    assert(queue.init() == ESP_OK);
    queue.push(make_event(100, 150, EventOrigin::BOTAO));
    queue.push(make_event(200, 250, EventOrigin::APP));
    queue.push(make_event(300, 350, EventOrigin::AGENDAMENTO));
    assert(queue.size() == 3);

    FakeBleServer server;
    BleEventProxy proxy(server, queue);
    proxy.begin();

    const size_t removed = proxy.handle_confirm(R"({"confirmados":[100,300]})");
    assert(removed == 2);
    assert(queue.size() == 1);

    Event remaining{};
    assert(queue.at(0, &remaining) == ESP_OK);
    assert(remaining.timestamp_inicio == 200);

    // Fila republicada apos a confirmacao.
    const std::string json = server.read(BleEventProxy::kPendingUuid);
    assert(json.find("\"inicio\":200") != std::string::npos);
    assert(json.find("\"inicio\":100") == std::string::npos);
    std::cout << "OK: test_confirm_removes_only_confirmed_subset\n";
}

static void test_confirm_without_ids_keeps_queue() {
    MockNvs::reset();
    EventQueueStore queue;
    assert(queue.init() == ESP_OK);
    queue.push(make_event(100, 150, EventOrigin::BOTAO));

    FakeBleServer server;
    BleEventProxy proxy(server, queue);
    proxy.begin();

    assert(proxy.handle_confirm(R"({"confirmados":[]})") == 0);
    assert(proxy.handle_confirm(R"({})") == 0);
    assert(queue.size() == 1);
    std::cout << "OK: test_confirm_without_ids_keeps_queue\n";
}

int main() {
    test_publishes_pending_queue_as_json_array();
    test_confirm_removes_only_confirmed_subset();
    test_confirm_without_ids_keeps_queue();
    std::cout << "Todos os testes de BleEventProxy passaram.\n";
    return 0;
}
