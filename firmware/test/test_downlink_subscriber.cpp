#include <cassert>
#include <iostream>
#include <string>

#include "mock_mqtt_client.hpp"
#include "app/downlink_subscriber.hpp"
#include "core/mqtt_topics.hpp"

using cafey::app::DownlinkSubscriber;
using cafey::core::MqttTopics;

static bool has_wildcard(const std::string& topic) {
    return topic.find('+') != std::string::npos || topic.find('#') != std::string::npos;
}

static void test_subscribes_exact_downlink_topics_qos1() {
    FakeMqttClient client;
    MqttTopics topics("cafey-001");
    DownlinkSubscriber subscriber(client, topics);

    assert(subscriber.subscribe_all());
    assert(client.subscribes.size() == 2);

    assert(client.subscribes[0].topic == "dispositivos/cafey-001/comando");
    assert(client.subscribes[1].topic == "dispositivos/cafey-001/agendamentos");
    for (const auto& sub : client.subscribes) {
        assert(sub.qos == 1);
        // Nome exato: retain do AWS IoT Core nao chega a filtro com wildcard.
        assert(!has_wildcard(sub.topic));
    }
    std::cout << "OK: test_subscribes_exact_downlink_topics_qos1\n";
}

static void test_subscribe_all_reports_failure() {
    FakeMqttClient client;
    client.subscribe_result = false;
    MqttTopics topics("cafey-001");
    DownlinkSubscriber subscriber(client, topics);

    assert(!subscriber.subscribe_all());
    // Ainda tenta ambas as assinaturas mesmo com falha.
    assert(client.subscribes.size() == 2);
    std::cout << "OK: test_subscribe_all_reports_failure\n";
}

int main() {
    test_subscribes_exact_downlink_topics_qos1();
    test_subscribe_all_reports_failure();
    std::cout << "Todos os testes de DownlinkSubscriber passaram.\n";
    return 0;
}
