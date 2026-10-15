#include <cstdint>
#include <iostream>

#include "app/timezone_offset.hpp"

#define TEST_ASSERT(cond, msg)                                                  \
    do {                                                                       \
        if (!(cond)) {                                                         \
            std::cerr << "FAILED: " << msg << " (" << __FILE__ << ":"          \
                      << __LINE__ << ")" << std::endl;                         \
            return 1;                                                          \
        }                                                                      \
    } while (0)

using cafey::app::utc_offset_s;

int test_known_brazilian_zones() {
    int32_t offset = 0;
    TEST_ASSERT(utc_offset_s("America/Sao_Paulo", &offset), "Sao_Paulo conhecido");
    TEST_ASSERT(offset == -3 * 3600, "Sao_Paulo e UTC-3");
    TEST_ASSERT(utc_offset_s("America/Noronha", &offset) && offset == -2 * 3600,
                "Noronha e UTC-2");
    TEST_ASSERT(utc_offset_s("America/Manaus", &offset) && offset == -4 * 3600,
                "Manaus e UTC-4");
    TEST_ASSERT(utc_offset_s("America/Rio_Branco", &offset) && offset == -5 * 3600,
                "Rio_Branco e UTC-5");

    std::cout << "[PASS] test_known_brazilian_zones" << std::endl;
    return 0;
}

int test_utc() {
    int32_t offset = 123;
    TEST_ASSERT(utc_offset_s("UTC", &offset) && offset == 0, "UTC e offset 0");
    offset = 123;
    TEST_ASSERT(utc_offset_s("Etc/UTC", &offset) && offset == 0, "Etc/UTC e offset 0");

    std::cout << "[PASS] test_utc" << std::endl;
    return 0;
}

int test_unknown_zone_is_rejected() {
    int32_t offset = 123;
    TEST_ASSERT(!utc_offset_s("Europe/Lisbon", &offset), "fuso fora da tabela e desconhecido");
    TEST_ASSERT(!utc_offset_s("", &offset), "fuso vazio e desconhecido");
    TEST_ASSERT(offset == 123, "fuso desconhecido nao altera a saida");

    std::cout << "[PASS] test_unknown_zone_is_rejected" << std::endl;
    return 0;
}

int main() {
    std::cout << "Running Cafey timezone offset (FW-23) Unit Tests..." << std::endl;
    if (test_known_brazilian_zones()) return 1;
    if (test_utc()) return 1;
    if (test_unknown_zone_is_rejected()) return 1;
    std::cout << "All timezone offset tests PASSED!" << std::endl;
    return 0;
}
