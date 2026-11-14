#pragma once

#include <cstddef>
#include <cstdint>

#if defined(ESP_PLATFORM)
#include "driver/gpio.h"
#include "esp_err.h"
#else
#include "mock_esp_gpio.hpp"
#endif

namespace cafey::drivers {

/**
 * @brief Leitura do DHT11 (exigencia da prova: temperatura e umidade).
 *
 * Temperatura em decimos de grau Celsius (245 = 24,5 C) para nao usar float na
 * fila de mensagens. O DHT11 classico so resolve graus inteiros (+-2 C); as
 * variantes mais novas trazem o decimo no 4o byte, e o decodificador aceita as
 * duas.
 */
struct Dht11Reading {
    int16_t temperature_dc = 0; // decimos de grau Celsius
    uint8_t humidity_pct = 0;   // 0..100 %UR
};

enum class Dht11Status : uint8_t {
    Ok,
    NotInitialized,
    NoResponse,  // o sensor nao puxou a linha: sem alimentacao, fio solto ou pino errado
    Timeout,     // a resposta comecou e parou no meio (ruido, fio longo)
    BadChecksum, // 40 bits recebidos, soma nao confere
    OutOfRange,  // soma confere, mas o valor e impossivel para o DHT11
};

/**
 * @brief Driver do DHT11 por fio unico (bit-bang, dreno aberto).
 *
 * Protocolo: o ESP32 puxa a linha para baixo por >= 18 ms e solta; o sensor
 * responde com 80 us baixo + 80 us alto e envia 40 bits (cada um: 50 us baixo +
 * 26-28 us alto para "0" ou ~70 us alto para "1"). A etapa dos 40 bits dura
 * ~4 ms e roda com interrupcoes desligadas neste nucleo; isso e aceitavel a
 * cada 5 s, mas nao cabe em laco rapido.
 *
 * Intervalo minimo entre leituras do sensor: 1 s (datasheet). Quem chama
 * (`app::Ambiente`) respeita isso.
 */
class Dht11 {
public:
    explicit Dht11(gpio_num_t pin = GPIO_NUM_33) : pin_(pin) {}

    /** Configura o pino como dreno aberto com pull-up interno e solta a linha. */
    esp_err_t init();

    /**
     * @brief Faz uma leitura completa. Bloqueia ~25 ms. Fora do alvo (testes de
     * host) nao ha sensor e devolve `NoResponse`.
     */
    Dht11Status read(Dht11Reading& out);

    [[nodiscard]] bool is_initialized() const noexcept { return initialized_; }
    [[nodiscard]] gpio_num_t pin() const noexcept { return pin_; }

    /**
     * @brief Converte a duracao do nivel alto de cada um dos 40 bits em 5 bytes.
     * Bit "1" quando o pulso alto passa de `kBitThresholdUs`.
     */
    static void pulses_to_frame(const uint8_t high_us[40], uint8_t frame[5]);

    /**
     * @brief Valida a soma e converte o quadro de 5 bytes em leitura.
     * Faixas aceitas: temperatura -40..80 C, umidade 0..100 %.
     */
    static Dht11Status decode_frame(const uint8_t frame[5], Dht11Reading& out);

    /** Limiar entre bit 0 (~27 us) e bit 1 (~70 us), em microssegundos. */
    static constexpr uint8_t kBitThresholdUs = 48;

private:
    gpio_num_t pin_;
    bool initialized_ = false;
};

} // namespace cafey::drivers
