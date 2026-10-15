#include <cassert>
#include <cstring>
#include <iostream>
#include <string>

#include "app/schedule_payload.hpp"
#include "storage/schedule.hpp"

using cafey::app::parse_schedule_payload;
using cafey::app::SchedulePayloadMeta;
using cafey::storage::Schedule;

static const char* kPayload = R"({
  "versao": 7,
  "timezone": "America/Sao_Paulo",
  "duracaoS": 300,
  "agendamentos": [
    { "id": "11111111-1111-1111-1111-111111111111", "hora": "07:05", "diasSemana": 62, "ativo": true },
    { "id": "22222222-2222-2222-2222-222222222222", "hora": "18:30", "diasSemana": 1, "ativo": false }
  ]
})";

static void test_parses_metadata() {
    Schedule list[16];
    size_t count = 0;
    SchedulePayloadMeta meta{};
    assert(parse_schedule_payload(kPayload, list, 16, &count, &meta));
    assert(meta.version == 7);
    assert(meta.has_duracao);
    assert(meta.duracao_s == 300);
    assert(meta.timezone == "America/Sao_Paulo"); // FW-23
    std::cout << "OK: test_parses_metadata\n";
}

static void test_parses_schedule_list() {
    Schedule list[16];
    size_t count = 0;
    SchedulePayloadMeta meta{};
    assert(parse_schedule_payload(kPayload, list, 16, &count, &meta));
    assert(count == 2);

    assert(std::memcmp(list[0].id, "11111111-1111-1111-1111-111111111111", 36) == 0);
    assert(list[0].hour == 7);
    assert(list[0].minute == 5);
    assert(list[0].days_of_week == 62);
    assert(list[0].active);

    assert(list[1].hour == 18);
    assert(list[1].minute == 30);
    assert(list[1].days_of_week == 1);
    assert(!list[1].active);
    std::cout << "OK: test_parses_schedule_list\n";
}

static void test_empty_list_replaces_with_zero() {
    Schedule list[16];
    size_t count = 99;
    SchedulePayloadMeta meta{};
    assert(parse_schedule_payload(R"({"versao":9,"agendamentos":[]})", list, 16, &count, &meta));
    assert(count == 0);
    assert(meta.version == 9);
    assert(meta.timezone.empty()); // sem `timezone` no payload
    std::cout << "OK: test_empty_list_replaces_with_zero\n";
}

static void test_respects_max_capacity() {
    Schedule list[1];
    size_t count = 0;
    SchedulePayloadMeta meta{};
    assert(parse_schedule_payload(kPayload, list, 1, &count, &meta));
    assert(count == 1);
    std::cout << "OK: test_respects_max_capacity\n";
}

static void test_rejects_null_out() {
    size_t count = 0;
    assert(!parse_schedule_payload(kPayload, nullptr, 16, &count, nullptr));
    std::cout << "OK: test_rejects_null_out\n";
}

// #200: duracaoS por item (PR #203); item sem duracaoS herda a do topo.
static void test_parses_item_duration_with_fallback() {
    const char* payload = R"({
      "versao": 8,
      "timezone": "America/Sao_Paulo",
      "duracaoS": 300,
      "agendamentos": [
        { "id": "11111111-1111-1111-1111-111111111111", "hora": "07:05", "diasSemana": 62, "ativo": true, "duracaoS": 480 },
        { "id": "22222222-2222-2222-2222-222222222222", "hora": "18:30", "diasSemana": 1, "ativo": true }
      ]
    })";
    Schedule list[16];
    size_t count = 0;
    SchedulePayloadMeta meta{};
    assert(parse_schedule_payload(payload, list, 16, &count, &meta));
    assert(count == 2);
    assert(meta.duracao_s == 300);
    assert(list[0].duration_s == 480);
    assert(list[1].duration_s == 300);
    std::cout << "OK: test_parses_item_duration_with_fallback" << std::endl;
}

static void test_item_without_any_duration_is_zero() {
    Schedule list[16];
    size_t count = 0;
    SchedulePayloadMeta meta{};
    assert(parse_schedule_payload(
        R"({"versao":1,"agendamentos":[{"id":"a","hora":"06:00","diasSemana":127,"ativo":true}]})",
        list, 16, &count, &meta));
    assert(count == 1);
    assert(list[0].duration_s == 0);
    std::cout << "OK: test_item_without_any_duration_is_zero" << std::endl;
}

int main() {
    test_parses_metadata();
    test_parses_schedule_list();
    test_empty_list_replaces_with_zero();
    test_respects_max_capacity();
    test_rejects_null_out();
    test_parses_item_duration_with_fallback();
    test_item_without_any_duration_is_zero();
    std::cout << "Todos os testes de parse_schedule_payload passaram.\n";
    return 0;
}
