// Testes do AO Ambiente: quando reporta, quando cala e como se recupera.

#include <iostream>
#include <vector>

#include "app/ambiente.hpp"

#define TEST_ASSERT(cond, msg)                                                  \
    do {                                                                       \
        if (!(cond)) {                                                         \
            std::cerr << "FAILED: " << msg << " (" << __FILE__ << ":"          \
                      << __LINE__ << ")" << std::endl;                         \
            return 1;                                                          \
        }                                                                      \
    } while (0)

using cafey::app::Ambiente;
using cafey::drivers::Dht11Reading;
using cafey::drivers::Dht11Status;

namespace {
class TestableAmbiente : public Ambiente {
public:
    void tick() { on_tick(); }
};

struct Harness {
    TestableAmbiente amb;
    std::vector<Ambiente::Sample> reports;
    Dht11Status next_status = Dht11Status::Ok;
    Dht11Reading next{240, 55};

    Harness() {
        amb.set_reader([this](Dht11Reading& out) {
            if (next_status == Dht11Status::Ok) out = next;
            return next_status;
        });
        amb.set_on_report([this](const Ambiente::Sample& s) { reports.push_back(s); });
    }
};
} // namespace

int test_first_valid_reading_reports() {
    Harness h;
    h.amb.tick();
    TEST_ASSERT(h.reports.size() == 1, "primeira leitura valida reporta");
    TEST_ASSERT(h.reports[0].valid && h.reports[0].temperature_dc == 240 &&
                    h.reports[0].humidity_pct == 55,
                "valores reportados");
    TEST_ASSERT(h.amb.last().valid, "ultimo valor guardado");
    return 0;
}

int test_unchanged_reading_is_silent_until_heartbeat() {
    Harness h;
    h.amb.tick();
    for (uint32_t i = 1; i < Ambiente::kReportEveryTicks; ++i) h.amb.tick();
    TEST_ASSERT(h.reports.size() == 1, "valor igual nao reporta antes do heartbeat");
    h.amb.tick();
    TEST_ASSERT(h.reports.size() == 2, "heartbeat reporta o valor igual");
    return 0;
}

int test_change_reports_immediately() {
    Harness h;
    h.amb.tick();
    h.next.temperature_dc = 250;
    h.amb.tick();
    TEST_ASSERT(h.reports.size() == 2 && h.reports[1].temperature_dc == 250, "mudanca de temperatura");
    h.next.humidity_pct = 60;
    h.amb.tick();
    TEST_ASSERT(h.reports.size() == 3 && h.reports[2].humidity_pct == 60, "mudanca de umidade");
    return 0;
}

int test_isolated_failure_is_tolerated() {
    Harness h;
    h.amb.tick();
    h.next_status = Dht11Status::BadChecksum;
    for (uint8_t i = 0; i + 1 < Ambiente::kFailuresToFault; ++i) h.amb.tick();
    TEST_ASSERT(h.reports.size() == 1, "poucas falhas nao reportam indisponivel");
    TEST_ASSERT(h.amb.last().valid, "valor anterior mantido");
    h.next_status = Dht11Status::Ok;
    h.amb.tick();
    TEST_ASSERT(h.amb.consecutive_failures() == 0, "leitura boa zera as falhas");
    return 0;
}

int test_persistent_failure_reports_unavailable_once() {
    Harness h;
    h.amb.tick();
    h.next_status = Dht11Status::NoResponse;
    for (int i = 0; i < 10; ++i) h.amb.tick();
    TEST_ASSERT(h.reports.size() == 2, "um unico reporte de indisponivel");
    TEST_ASSERT(!h.reports[1].valid, "reporte de indisponivel");
    TEST_ASSERT(!h.amb.last().valid, "nao afirma mais o valor antigo");
    return 0;
}

int test_recovery_reports_again_even_if_value_equal() {
    Harness h;
    h.amb.tick();
    h.next_status = Dht11Status::Timeout;
    for (uint8_t i = 0; i < Ambiente::kFailuresToFault; ++i) h.amb.tick();
    h.next_status = Dht11Status::Ok; // mesmo valor de antes da falha
    h.amb.tick();
    TEST_ASSERT(h.reports.size() == 3 && h.reports[2].valid, "recuperacao reporta valido");
    return 0;
}

int test_no_reader_does_nothing() {
    TestableAmbiente amb;
    int calls = 0;
    amb.set_on_report([&](const Ambiente::Sample&) { ++calls; });
    amb.tick();
    TEST_ASSERT(calls == 0, "sem leitor (sensor nao anexado) nao reporta");
    return 0;
}

int main() {
    std::cout << "Running Cafey Ambiente AO Unit Tests..." << std::endl;
    int tests = 0;
    if (test_first_valid_reading_reports()) return 1; ++tests;
    if (test_unchanged_reading_is_silent_until_heartbeat()) return 1; ++tests;
    if (test_change_reports_immediately()) return 1; ++tests;
    if (test_isolated_failure_is_tolerated()) return 1; ++tests;
    if (test_persistent_failure_reports_unavailable_once()) return 1; ++tests;
    if (test_recovery_reports_again_even_if_value_equal()) return 1; ++tests;
    if (test_no_reader_does_nothing()) return 1; ++tests;
    std::cout << "All " << tests << " Ambiente tests PASSED!" << std::endl;
    return 0;
}
