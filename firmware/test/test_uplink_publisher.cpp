#include <cassert>
#include <iostream>

#include "mock_mqtt_client.hpp"
#include "app/uplink_publisher.hpp"
#include "core/mqtt_topics.hpp"

using cafey::app::UplinkPublisher;
using cafey::core::MqttConfig;
using cafey::core::MqttTopics;

static void test_estado_published_with_retain() {
    FakeMqttClient client;
    MqttTopics topics("cafey-001");
    UplinkPublisher pub(client, topics);

    assert(pub.publish_estado("{\"estado\":\"PREPARANDO\"}"));
    assert(client.publishes.size() == 1);
    assert(client.publishes[0].topic == "dispositivos/cafey-001/estado");
    assert(client.publishes[0].qos == 1);
    assert(client.publishes[0].retain == true);
    std::cout << "OK: test_estado_published_with_retain\n";
}

static void test_eventos_published_without_retain() {
    FakeMqttClient client;
    MqttTopics topics("cafey-001");
    UplinkPublisher pub(client, topics);

    assert(pub.publish_evento("{\"resultado\":\"CONCLUIDO\"}"));
    assert(client.publishes[0].topic == "dispositivos/cafey-001/eventos");
    assert(client.publishes[0].qos == 1);
    assert(client.publishes[0].retain == false);
    std::cout << "OK: test_eventos_published_without_retain\n";
}

static void test_saude_published_without_retain() {
    FakeMqttClient client;
    MqttTopics topics("cafey-001");
    UplinkPublisher pub(client, topics);

    assert(pub.publish_saude("{\"online\":true}"));
    assert(client.publishes[0].topic == "dispositivos/cafey-001/saude");
    assert(client.publishes[0].retain == false);
    std::cout << "OK: test_saude_published_without_retain\n";
}

static void test_last_will_is_retained_offline_on_saude() {
    MqttTopics topics("cafey-001");
    MqttConfig config;
    UplinkPublisher::make_last_will(topics, config);

    assert(config.last_will_topic == "dispositivos/cafey-001/saude");
    assert(config.last_will_payload == "{\"online\":false}");
    assert(config.last_will_retain == true);
    std::cout << "OK: test_last_will_is_retained_offline_on_saude\n";
}

int main() {
    test_estado_published_with_retain();
    test_eventos_published_without_retain();
    test_saude_published_without_retain();
    test_last_will_is_retained_offline_on_saude();
    std::cout << "Todos os testes de UplinkPublisher passaram.\n";
    return 0;
}
