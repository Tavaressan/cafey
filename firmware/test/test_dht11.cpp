// Testes do driver DHT11: decodificacao de pulsos, soma de verificacao e faixas.
// A leitura no fio depende de temporizacao de microssegundos e do sensor real;
// aqui se cobre tudo o que e puro (a parte que erra silenciosamente).

#include <cstring>
#include <memory>
#include <iostream>

#include "dht11.hpp"

#define TEST_ASSERT(cond, msg)                                                  \
    do {                                                                       \
        if (!(cond)) {                                                         \
            std::cerr << "FAILED: " << msg << " (" << __FILE__ << ":"          \
                      << __LINE__ << ")" << std::endl;                         \
            return 1;                                                          \
        }                                                                      \
    } while (0)

using cafey::drivers::Dht11;
using cafey::drivers::Dht11Reading;
using cafey::drivers::Dht11Status;

namespace {
// Monta o quadro (umidade, 0, temperatura, decimal) com a soma correta.
void make_frame(uint8_t rh, uint8_t t, uint8_t t_dec, uint8_t out[5], int sum_error = 0) {
    out[0] = rh;
    out[1] = 0;
    out[2] = t;
    out[3] = t_dec;
    out[4] = static_cast<uint8_t>(out[0] + out[1] + out[2] + out[3] + sum_error);
}

// Pulsos altos de cada bit: ~27 us para 0 e ~70 us para 1, do bit mais significativo.
void frame_to_pulses(const uint8_t frame[5], uint8_t pulses[40]) {
    for (int bit = 0; bit < 40; ++bit) {
        const bool one = (frame[bit / 8] >> (7 - bit % 8)) & 1u;
        pulses[bit] = one ? 70 : 27;
    }
}
} // namespace

int test_pulses_roundtrip() {
    uint8_t frame[5];
    make_frame(55, 24, 0, frame);
    uint8_t pulses[40];
    frame_to_pulses(frame, pulses);
    uint8_t decoded[5];
    Dht11::pulses_to_frame(pulses, decoded);
    TEST_ASSERT(std::memcmp(frame, decoded, 5) == 0, "pulsos -> quadro preserva os 40 bits");
    return 0;
}

int test_pulse_threshold() {
    uint8_t pulses[40];
    for (auto& p : pulses) p = Dht11::kBitThresholdUs; // exatamente no limiar: ainda e 0
    uint8_t frame[5];
    Dht11::pulses_to_frame(pulses, frame);
    for (int i = 0; i < 5; ++i) TEST_ASSERT(frame[i] == 0, "limiar exato conta como bit 0");
    for (auto& p : pulses) p = Dht11::kBitThresholdUs + 1;
    Dht11::pulses_to_frame(pulses, frame);
    for (int i = 0; i < 5; ++i) TEST_ASSERT(frame[i] == 0xFF, "acima do limiar conta como bit 1");
    return 0;
}

int test_decode_valid() {
    uint8_t frame[5];
    make_frame(55, 24, 0, frame);
    Dht11Reading r;
    TEST_ASSERT(Dht11::decode_frame(frame, r) == Dht11Status::Ok, "quadro valido");
    TEST_ASSERT(r.temperature_dc == 240, "24 C = 240 decimos");
    TEST_ASSERT(r.humidity_pct == 55, "umidade 55 %");
    return 0;
}

int test_decode_decimal_variant() {
    uint8_t frame[5];
    make_frame(40, 23, 7, frame); // variante nova: 23,7 C
    Dht11Reading r;
    TEST_ASSERT(Dht11::decode_frame(frame, r) == Dht11Status::Ok, "quadro com decimal");
    TEST_ASSERT(r.temperature_dc == 237, "23,7 C = 237 decimos");
    return 0;
}

int test_decode_negative_temperature() {
    uint8_t frame[5];
    make_frame(40, 5, 0x80 | 3, frame); // -5,3 C (bit 7 do byte decimal = sinal)
    Dht11Reading r;
    TEST_ASSERT(Dht11::decode_frame(frame, r) == Dht11Status::Ok, "temperatura negativa");
    TEST_ASSERT(r.temperature_dc == -53, "-5,3 C = -53 decimos");
    return 0;
}

int test_decode_bad_checksum_keeps_output() {
    uint8_t frame[5];
    make_frame(55, 24, 0, frame, /*sum_error=*/1);
    Dht11Reading r{999, 99};
    TEST_ASSERT(Dht11::decode_frame(frame, r) == Dht11Status::BadChecksum, "soma errada");
    TEST_ASSERT(r.temperature_dc == 999 && r.humidity_pct == 99, "falha nao suja a saida");
    return 0;
}

int test_decode_out_of_range() {
    Dht11Reading r;
    uint8_t frame[5];
    make_frame(101, 24, 0, frame);
    TEST_ASSERT(Dht11::decode_frame(frame, r) == Dht11Status::OutOfRange, "umidade > 100");
    make_frame(50, 81, 0, frame);
    TEST_ASSERT(Dht11::decode_frame(frame, r) == Dht11Status::OutOfRange, "temperatura > 80");
    make_frame(50, 41, 0x80, frame);
    TEST_ASSERT(Dht11::decode_frame(frame, r) == Dht11Status::OutOfRange, "temperatura < -40");
    make_frame(0, 0, 0, frame); // tudo zero tem soma valida: sensor ausente em barramento baixo
    TEST_ASSERT(Dht11::decode_frame(frame, r) == Dht11Status::Ok, "0 C e 0 % sao faixa valida");
    return 0;
}

int test_init_configures_open_drain_with_pullup() {
    MockGpio::reset();
    Dht11 dht(GPIO_NUM_33);
    TEST_ASSERT(dht.read(*std::make_unique<Dht11Reading>()) == Dht11Status::NotInitialized,
                "ler antes de init falha");
    TEST_ASSERT(dht.init() == ESP_OK, "init");
    const auto& pin = MockGpio::get_pin(GPIO_NUM_33);
    TEST_ASSERT(pin.mode == GPIO_MODE_INPUT_OUTPUT_OD, "dreno aberto bidirecional");
    TEST_ASSERT(pin.pull_up == GPIO_PULLUP_ENABLE, "pull-up interno ligado");
    TEST_ASSERT(pin.level == 1, "linha solta (alta) apos init");
    TEST_ASSERT(dht.is_initialized(), "inicializado");
    return 0;
}

int test_init_propagates_gpio_error() {
    MockGpio::reset();
    MockGpio::config_ret_code = ESP_FAIL;
    Dht11 dht(GPIO_NUM_33);
    TEST_ASSERT(dht.init() == ESP_FAIL, "erro do gpio_config propaga");
    TEST_ASSERT(!dht.is_initialized(), "continua nao inicializado");
    MockGpio::reset();
    return 0;
}

int main() {
    std::cout << "Running Cafey DHT11 Driver Unit Tests..." << std::endl;
    int tests = 0;
    if (test_pulses_roundtrip()) return 1; ++tests;
    if (test_pulse_threshold()) return 1; ++tests;
    if (test_decode_valid()) return 1; ++tests;
    if (test_decode_decimal_variant()) return 1; ++tests;
    if (test_decode_negative_temperature()) return 1; ++tests;
    if (test_decode_bad_checksum_keeps_output()) return 1; ++tests;
    if (test_decode_out_of_range()) return 1; ++tests;
    if (test_init_configures_open_drain_with_pullup()) return 1; ++tests;
    if (test_init_propagates_gpio_error()) return 1; ++tests;
    std::cout << "All " << tests << " DHT11 tests PASSED!" << std::endl;
    return 0;
}
