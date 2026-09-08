#include <cassert>
#include <iostream>
#include <string>
#include <vector>

#include "app/ble_service.hpp"
#include "app/coffee_command.hpp"
#include "app/schedule_payload.hpp"
#include "mock_ble_server.hpp"
#include "storage/schedule.hpp"

using cafey::app::BleService;
using cafey::app::CoffeeAction;
using cafey::app::CoffeeCommand;
using cafey::app::SchedulePayloadMeta;
using cafey::storage::Schedule;

namespace {

struct Capture {
    std::vector<CoffeeCommand> commands;
    int schedule_calls = 0;
    std::vector<Schedule> last_schedules;
    SchedulePayloadMeta last_meta{};
};

BleService make_service(FakeBleServer& server, Capture& cap) {
    return BleService(
        server,
        [&cap](const CoffeeCommand& cmd) { cap.commands.push_back(cmd); },
        [&cap](const Schedule* list, size_t count, const SchedulePayloadMeta& meta) {
            cap.schedule_calls++;
            cap.last_schedules.assign(list, list + count);
            cap.last_meta = meta;
        });
}

} // namespace

static void test_registers_three_characteristics_and_advertises() {
    FakeBleServer server;
    Capture cap;
    BleService service = make_service(server, cap);

    assert(service.begin("cafey-001"));
    assert(server.started);
    assert(server.device_name == "cafey-001");

    assert(server.has(BleService::kCommandUuid));
    assert(server.has(BleService::kStateUuid));
    assert(server.has(BleService::kScheduleUuid));

    assert(server.props(BleService::kCommandUuid) & cafey::core::kBleWrite);
    assert(server.props(BleService::kStateUuid) & cafey::core::kBleRead);
    assert(server.props(BleService::kStateUuid) & cafey::core::kBleNotify);
    assert(server.props(BleService::kScheduleUuid) & cafey::core::kBleWrite);
    std::cout << "OK: test_registers_three_characteristics_and_advertises\n";
}

static void test_command_write_routes_to_domain_handler() {
    FakeBleServer server;
    Capture cap;
    BleService service = make_service(server, cap);
    service.begin("cafey-001");

    server.client_write(BleService::kCommandUuid, R"({"acao":"LIGAR","duracaoS":300})");
    assert(cap.commands.size() == 1);
    assert(cap.commands[0].action == CoffeeAction::Ligar);
    assert(cap.commands[0].duracao_s == 300);

    // Comando invalido nao aciona o dominio.
    server.client_write(BleService::kCommandUuid, R"({"acao":"NOPE"})");
    assert(cap.commands.size() == 1);
    std::cout << "OK: test_command_write_routes_to_domain_handler\n";
}

static void test_schedule_write_routes_full_list() {
    FakeBleServer server;
    Capture cap;
    BleService service = make_service(server, cap);
    service.begin("cafey-001");

    server.client_write(
        BleService::kScheduleUuid,
        R"({"versao":4,"agendamentos":[{"id":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","hora":"06:15","diasSemana":2,"ativo":true}]})");

    assert(cap.schedule_calls == 1);
    assert(cap.last_schedules.size() == 1);
    assert(cap.last_schedules[0].hour == 6);
    assert(cap.last_schedules[0].minute == 15);
    assert(cap.last_meta.version == 4);
    std::cout << "OK: test_schedule_write_routes_full_list\n";
}

static void test_publish_state_updates_and_notifies() {
    FakeBleServer server;
    Capture cap;
    BleService service = make_service(server, cap);
    service.begin("cafey-001");

    const std::string payload = R"({"estado":"PREPARANDO","desde":"2026-09-01T13:00:04Z"})";
    assert(service.publish_state(payload));
    assert(server.read(BleService::kStateUuid) == payload);
    assert(!server.notifications.empty());
    assert(server.notifications.back().first == BleService::kStateUuid);
    std::cout << "OK: test_publish_state_updates_and_notifies\n";
}

int main() {
    test_registers_three_characteristics_and_advertises();
    test_command_write_routes_to_domain_handler();
    test_schedule_write_routes_full_list();
    test_publish_state_updates_and_notifies();
    std::cout << "Todos os testes de BleService passaram.\n";
    return 0;
}
