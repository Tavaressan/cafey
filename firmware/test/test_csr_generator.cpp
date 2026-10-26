#include "csr_generator.hpp"
#include <cassert>
#include <cstdio>
#include <cstring>

using namespace cafey::core;

int main() {
    printf("Running CsrGenerator tests...\n");

    esp_err_t err = CsrGenerator::ensure_private_key();
    assert(err == ESP_OK);

    char csr_pem[2048];
    err = CsrGenerator::generate_csr("11111111-2222-3333-4444-555555555555", csr_pem, sizeof(csr_pem));
    assert(err == ESP_OK);
    assert(strstr(csr_pem, "BEGIN CERTIFICATE REQUEST") != nullptr);
    assert(strstr(csr_pem, "END CERTIFICATE REQUEST") != nullptr);

    err = CsrGenerator::print_csr_to_serial("11111111-2222-3333-4444-555555555555");
    assert(err == ESP_OK);

    printf("All CsrGenerator tests passed successfully!\n");
    return 0;
}
