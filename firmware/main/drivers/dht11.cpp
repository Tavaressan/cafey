#include "drivers/dht11.hpp"

#if defined(ESP_PLATFORM)
#include "esp_rom_sys.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#endif

namespace cafey::drivers {

namespace {
constexpr int kMinTemperatureDc = -400; // -40,0 C
constexpr int kMaxTemperatureDc = 800;  //  80,0 C (o DHT11 so garante 0..50 C)
constexpr uint8_t kMaxHumidity = 100;
} // namespace

void Dht11::pulses_to_frame(const uint8_t high_us[40], uint8_t frame[5]) {
    for (std::size_t i = 0; i < 5; ++i) frame[i] = 0;
    for (std::size_t bit = 0; bit < 40; ++bit) {
        frame[bit / 8] = static_cast<uint8_t>(frame[bit / 8] << 1);
        if (high_us[bit] > kBitThresholdUs) frame[bit / 8] |= 1u;
    }
}

Dht11Status Dht11::decode_frame(const uint8_t frame[5], Dht11Reading& out) {
    const uint8_t sum = static_cast<uint8_t>(frame[0] + frame[1] + frame[2] + frame[3]);
    if (sum != frame[4]) return Dht11Status::BadChecksum;

    // Byte 0/1: umidade (inteiro, decimal). Byte 2/3: temperatura (inteiro,
    // decimal). O DHT11 classico manda decimal 0; nas variantes novas o bit 7
    // do byte 3 e o sinal.
    const int humidity = frame[0];
    int temperature_dc = frame[2] * 10 + (frame[3] & 0x7F);
    if (frame[3] & 0x80) temperature_dc = -temperature_dc;

    if (humidity > kMaxHumidity || temperature_dc < kMinTemperatureDc ||
        temperature_dc > kMaxTemperatureDc) {
        return Dht11Status::OutOfRange;
    }
    out.temperature_dc = static_cast<int16_t>(temperature_dc);
    out.humidity_pct = static_cast<uint8_t>(humidity);
    return Dht11Status::Ok;
}

#if defined(ESP_PLATFORM)

esp_err_t Dht11::init() {
    const gpio_config_t cfg = {
        .pin_bit_mask = 1ULL << pin_,
        .mode = GPIO_MODE_INPUT_OUTPUT_OD, // escreve 0 = puxa; escreve 1 = solta e le
        .pull_up_en = GPIO_PULLUP_ENABLE,  // o modulo do kit ja traz um pull-up de 10 k
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type = GPIO_INTR_DISABLE,
    };
    esp_err_t err = gpio_config(&cfg);
    if (err != ESP_OK) return err;
    gpio_set_level(pin_, 1);
    initialized_ = true;
    return ESP_OK;
}

namespace {
// Espera a linha chegar a `level`. Devolve os microssegundos gastos, ou -1 no timeout.
int wait_for_level(gpio_num_t pin, int level, int timeout_us) {
    const int64_t start = esp_timer_get_time();
    while (gpio_get_level(pin) != level) {
        if (esp_timer_get_time() - start > timeout_us) return -1;
    }
    return static_cast<int>(esp_timer_get_time() - start);
}
} // namespace

Dht11Status Dht11::read(Dht11Reading& out) {
    if (!initialized_) return Dht11Status::NotInitialized;

    // Pedido de leitura: >= 18 ms em nivel baixo, fora da secao critica para nao
    // travar o Wi-Fi e o BLE por 20 ms.
    gpio_set_level(pin_, 0);
    vTaskDelay(pdMS_TO_TICKS(20));

    uint8_t high_us[40] = {};
    Dht11Status status = Dht11Status::Ok;

    static portMUX_TYPE mux = portMUX_INITIALIZER_UNLOCKED;
    portENTER_CRITICAL(&mux);
    gpio_set_level(pin_, 1); // solta a linha; o pull-up a leva a nivel alto
    esp_rom_delay_us(30);

    // Resposta: ~80 us baixo, ~80 us alto, depois o primeiro bit comeca baixo.
    if (wait_for_level(pin_, 0, 100) < 0) {
        status = Dht11Status::NoResponse;
    } else if (wait_for_level(pin_, 1, 120) < 0 || wait_for_level(pin_, 0, 120) < 0) {
        status = Dht11Status::Timeout;
    } else {
        for (int bit = 0; bit < 40; ++bit) {
            if (wait_for_level(pin_, 1, 80) < 0) { // fim do nivel baixo (~50 us)
                status = Dht11Status::Timeout;
                break;
            }
            const int width = wait_for_level(pin_, 0, 120); // largura do nivel alto
            if (width < 0) {
                status = Dht11Status::Timeout;
                break;
            }
            high_us[bit] = static_cast<uint8_t>(width > 255 ? 255 : width);
        }
    }
    portEXIT_CRITICAL(&mux);

    if (status != Dht11Status::Ok) return status;

    uint8_t frame[5];
    pulses_to_frame(high_us, frame);
    return decode_frame(frame, out);
}

#else // testes de host: nao ha sensor na outra ponta da linha

esp_err_t Dht11::init() {
    const gpio_config_t cfg = {
        .pin_bit_mask = 1ULL << pin_,
        .mode = GPIO_MODE_INPUT_OUTPUT_OD,
        .pull_up_en = GPIO_PULLUP_ENABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type = GPIO_INTR_DISABLE,
    };
    const esp_err_t err = gpio_config(&cfg);
    if (err != ESP_OK) return err;
    gpio_set_level(pin_, 1);
    initialized_ = true;
    return ESP_OK;
}

Dht11Status Dht11::read(Dht11Reading&) {
    return initialized_ ? Dht11Status::NoResponse : Dht11Status::NotInitialized;
}

#endif

} // namespace cafey::drivers
