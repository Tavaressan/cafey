#include <cstring>
#include <iostream>

#include "mock_esp_nvs.hpp"
#include "event_id_generator.hpp"

#define TEST_ASSERT(cond, msg)                                                  \
    do {                                                                       \
        if (!(cond)) {                                                         \
            std::cerr << "FAILED: " << msg << " (" << __FILE__ << ":"          \
                      << __LINE__ << ")" << std::endl;                         \
            return 1;                                                          \
        }                                                                      \
    } while (0)

using cafey::storage::EventIdGenerator;

int test_starts_at_zero_when_nothing_persisted() {
    MockNvs::reset();
    EventIdGenerator gen;
    TEST_ASSERT(gen.init(0x2a) == ESP_OK, "init deve suceder sem dados previos");
    TEST_ASSERT(gen.current_seq() == 0, "seq comeca em 0");

    std::cout << "[PASS] test_starts_at_zero_when_nothing_persisted" << std::endl;
    return 0;
}

int test_next_formats_bootid_colon_seq_and_increments() {
    MockNvs::reset();
    EventIdGenerator gen;
    gen.init(0x2a);

    char id[EventIdGenerator::kMaxIdLen];
    TEST_ASSERT(gen.next(id, sizeof(id)) == ESP_OK, "next() deve suceder");
    TEST_ASSERT(std::strcmp(id, "0000002a:0") == 0, "primeiro id = bootId hex : seq 0");
    TEST_ASSERT(gen.current_seq() == 1, "seq avanca para 1");

    TEST_ASSERT(gen.next(id, sizeof(id)) == ESP_OK, "next() deve suceder");
    TEST_ASSERT(std::strcmp(id, "0000002a:1") == 0, "segundo id usa seq 1");

    std::cout << "[PASS] test_next_formats_bootid_colon_seq_and_increments" << std::endl;
    return 0;
}

int test_seq_survives_reboot_with_new_bootid() {
    MockNvs::reset();
    char id[EventIdGenerator::kMaxIdLen];
    {
        EventIdGenerator gen;
        gen.init(0x2a);
        gen.next(id, sizeof(id));
        gen.next(id, sizeof(id));
        gen.next(id, sizeof(id)); // seq agora = 3
    }

    // Reboot: nova instancia, novo bootId; seq deve continuar de onde parou.
    EventIdGenerator gen;
    TEST_ASSERT(gen.init(0x63) == ESP_OK, "init apos reboot");
    TEST_ASSERT(gen.current_seq() == 3, "seq persistido sobrevive ao reboot");
    TEST_ASSERT(gen.next(id, sizeof(id)) == ESP_OK, "next() apos reboot");
    TEST_ASSERT(std::strcmp(id, "00000063:3") == 0, "id combina novo bootId com seq continuado");

    std::cout << "[PASS] test_seq_survives_reboot_with_new_bootid" << std::endl;
    return 0;
}

int test_rejects_small_buffer_and_uninitialized_use() {
    MockNvs::reset();
    char small[4];
    EventIdGenerator gen;
    TEST_ASSERT(gen.next(small, sizeof(small)) == ESP_ERR_INVALID_STATE,
                "next() sem init -> ESP_ERR_INVALID_STATE");

    gen.init(0x1);
    TEST_ASSERT(gen.next(small, sizeof(small)) == ESP_ERR_INVALID_ARG,
                "buffer pequeno -> ESP_ERR_INVALID_ARG");
    TEST_ASSERT(gen.next(nullptr, EventIdGenerator::kMaxIdLen) == ESP_ERR_INVALID_ARG,
                "buffer nulo -> ESP_ERR_INVALID_ARG");

    std::cout << "[PASS] test_rejects_small_buffer_and_uninitialized_use" << std::endl;
    return 0;
}

int main() {
    std::cout << "Running Cafey EventIdGenerator Unit Tests..." << std::endl;
    if (test_starts_at_zero_when_nothing_persisted()) return 1;
    if (test_next_formats_bootid_colon_seq_and_increments()) return 1;
    if (test_seq_survives_reboot_with_new_bootid()) return 1;
    if (test_rejects_small_buffer_and_uninitialized_use()) return 1;
    std::cout << "All EventIdGenerator tests PASSED!" << std::endl;
    return 0;
}
