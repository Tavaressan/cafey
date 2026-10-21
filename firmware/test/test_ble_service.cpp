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
    int wifi_provision_calls = 0;
    std::string last_ssid;
    std::string last_password;
};

BleService make_service(FakeBleServer& server, Capture& cap) {
    return BleService(
        server,
        [&cap](const CoffeeCommand& cmd) { cap.commands.push_back(cmd); },
        [&cap](const Schedule* list, size_t count, const SchedulePayloadMeta& meta) {
            cap.schedule_calls++;
            cap.last_schedules.assign(list, list + count);
            cap.last_meta = meta;
        },
        [&cap](const std::string& ssid, const std::string& password) {
            cap.wifi_provision_calls++;
            cap.last_ssid = ssid;
            cap.last_password = password;
        });
}

} // namespace

static void test_registers_characteristics_and_advertises() {
    FakeBleServer server;
    Capture cap;
    BleService service = make_service(server, cap);

    assert(service.begin("cafey-001"));
    assert(server.started);
    assert(server.device_name == "cafey-001");

    assert(server.has(BleService::kCommandUuid));
    assert(server.has(BleService::kStateUuid));
    assert(server.has(BleService::kScheduleUuid));
    assert(server.has(BleService::kWifiProvisionUuid));
    assert(server.has(BleService::kWifiStatusUuid));
    assert(server.has(BleService::kDeviceInfoUuid));

    assert(server.props(BleService::kCommandUuid) & cafey::core::kBleWrite);
    assert(server.props(BleService::kStateUuid) & cafey::core::kBleRead);
    assert(server.props(BleService::kStateUuid) & cafey::core::kBleNotify);
    assert(server.props(BleService::kScheduleUuid) & cafey::core::kBleWrite);
    assert(server.props(BleService::kWifiProvisionUuid) & cafey::core::kBleWrite);
    assert(server.props(BleService::kWifiStatusUuid) & cafey::core::kBleRead);
    assert(server.props(BleService::kWifiStatusUuid) & cafey::core::kBleNotify);
    assert(server.props(BleService::kDeviceInfoUuid) & cafey::core::kBleRead);

    // Leitura inicial de DeviceInfo
    const std::string dev_info = server.read(BleService::kDeviceInfoUuid);
    assert(dev_info.find("\"serial\":\"cafey-001\"") != std::string::npos);
    assert(dev_info.find("\"firmware\":") != std::string::npos);
    assert(dev_info.find("\"model\":") != std::string::npos);

    // Leitura inicial de WifiStatus
    const std::string wifi_status = server.read(BleService::kWifiStatusUuid);
    assert(wifi_status.find("\"state\":\"DISCONNECTED\"") != std::string::npos);

    std::cout << "OK: test_registers_characteristics_and_advertises\n";
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

static void test_wifi_provision_write_valid_payload() {
    FakeBleServer server;
    Capture cap;
    BleService service = make_service(server, cap);
    service.begin("cafey-001");

    server.client_write(BleService::kWifiProvisionUuid, R"({"ssid":"MinhaRede","password":"Senha123"})");
    assert(cap.wifi_provision_calls == 1);
    assert(cap.last_ssid == "MinhaRede");
    assert(cap.last_password == "Senha123");

    // Status deve emitir notificacao de CONNECTING
    const std::string status = server.read(BleService::kWifiStatusUuid);
    assert(status == R"({"state":"CONNECTING","ip":null,"error":null})");
    assert(!server.notifications.empty());
    assert(server.notifications.back().first == BleService::kWifiStatusUuid);
    assert(server.notifications.back().second == status);
    std::cout << "OK: test_wifi_provision_write_valid_payload\n";
}

static void test_wifi_provision_write_invalid_payload() {
    FakeBleServer server;
    Capture cap;
    BleService service = make_service(server, cap);
    service.begin("cafey-001");

    // Payload sem chave ssid
    server.client_write(BleService::kWifiProvisionUuid, R"({"password":"123"})");
    assert(cap.wifi_provision_calls == 0);
    std::string status = server.read(BleService::kWifiStatusUuid);
    assert(status.find("\"state\":\"INVALID_PAYLOAD\"") != std::string::npos);

    // Payload com ssid vazio
    server.client_write(BleService::kWifiProvisionUuid, R"({"ssid":"","password":"123"})");
    assert(cap.wifi_provision_calls == 0);
    status = server.read(BleService::kWifiStatusUuid);
    assert(status.find("\"state\":\"INVALID_PAYLOAD\"") != std::string::npos);

    // Payload com ssid > 32 bytes
    std::string long_ssid(33, 'a');
    server.client_write(BleService::kWifiProvisionUuid, "{\"ssid\":\"" + long_ssid + "\",\"password\":\"123\"}");
    assert(cap.wifi_provision_calls == 0);
    status = server.read(BleService::kWifiStatusUuid);
    assert(status.find("\"state\":\"INVALID_PAYLOAD\"") != std::string::npos);

    // Payload com password > 64 bytes
    std::string long_pwd(65, 'b');
    server.client_write(BleService::kWifiProvisionUuid, "{\"ssid\":\"Valido\",\"password\":\"" + long_pwd + "\"}");
    assert(cap.wifi_provision_calls == 0);
    status = server.read(BleService::kWifiStatusUuid);
    assert(status.find("\"state\":\"INVALID_PAYLOAD\"") != std::string::npos);

    std::cout << "OK: test_wifi_provision_write_invalid_payload\n";
}

static void test_publish_wifi_status_states() {
    FakeBleServer server;
    Capture cap;
    BleService service = make_service(server, cap);
    service.begin("cafey-001");

    assert(service.publish_wifi_status("CONNECTED", "192.168.1.150"));
    assert(server.read(BleService::kWifiStatusUuid) == R"({"state":"CONNECTED","ip":"192.168.1.150","error":null})");

    assert(service.publish_wifi_status("AUTH_FAILED", "", "Falha de autenticacao"));
    assert(server.read(BleService::kWifiStatusUuid) == R"({"state":"AUTH_FAILED","ip":null,"error":"Falha de autenticacao"})");

    assert(service.publish_wifi_status("TIMEOUT"));
    assert(server.read(BleService::kWifiStatusUuid) == R"({"state":"TIMEOUT","ip":null,"error":null})");

    std::cout << "OK: test_publish_wifi_status_states\n";
}

int main() {
    test_registers_characteristics_and_advertises();
    test_command_write_routes_to_domain_handler();
    test_schedule_write_routes_full_list();
    test_publish_state_updates_and_notifies();
    test_wifi_provision_write_valid_payload();
    test_wifi_provision_write_invalid_payload();
    test_publish_wifi_status_states();
    std::cout << "Todos os testes de BleService passaram.\n";
    return 0;
}
