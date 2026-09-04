/*
 * Cafey - Firmware do modulo IoT (ESP-IDF C++17)
 * Fatia de I/O local: Botao (GPIO27), Rele (GPIO26), LED RGB (GPIO21/22/23)
 *
 * Pinagem e eletrica conforme STATUS.md e esquematico KiCad:
 * - Rele (GPIO26): gatilho de nivel ALTO, resistor R1 pull-down 10k externo
 * - Botao (GPIO27): pull-up externo R2 10k + C3 100nF, solto=1, pressionado=0
 * - LED RGB: catodo comum (R=21, G=22, B=23)
 */

#include <cstdint>
#include <stdbool.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "driver/gpio.h"
#include "esp_log.h"
#include "drivers/relay.hpp"
#include "storage/schedule_store.hpp"
#include "storage/event_queue_store.hpp"

namespace cafey {

constexpr gpio_num_t PIN_RELAY  = GPIO_NUM_26;
constexpr gpio_num_t PIN_BUTTON = GPIO_NUM_27;
constexpr gpio_num_t PIN_LED_R  = GPIO_NUM_21;
constexpr gpio_num_t PIN_LED_G  = GPIO_NUM_22;
constexpr gpio_num_t PIN_LED_B  = GPIO_NUM_23;

constexpr uint32_t BOOT_BLINK_MS    = 1000;
constexpr uint32_t DEBOUNCE_POLL_MS = 10;
constexpr int      DEBOUNCE_SAMPLES = 3; /* 3 x 10 ms = 30 ms de nivel estavel */

static const char *TAG = "cafey";

void led_set(bool r, bool g, bool b) {
    gpio_set_level(PIN_LED_R, r ? 1 : 0);
    gpio_set_level(PIN_LED_G, g ? 1 : 0);
    gpio_set_level(PIN_LED_B, b ? 1 : 0);
}

void init_gpio() {
    // Configura pinos do LED RGB como saida
    const gpio_config_t led_cfg = {
        .pin_bit_mask = (1ULL << PIN_LED_R) | (1ULL << PIN_LED_G) | (1ULL << PIN_LED_B),
        .mode = GPIO_MODE_OUTPUT,
        .pull_up_en = GPIO_PULLUP_DISABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type = GPIO_INTR_DISABLE,
    };
    gpio_config(&led_cfg);
    led_set(false, false, false);

    // Configura pino do botao como entrada com pull-up
    const gpio_config_t btn_cfg = {
        .pin_bit_mask = (1ULL << PIN_BUTTON),
        .mode = GPIO_MODE_INPUT,
        .pull_up_en = GPIO_PULLUP_ENABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type = GPIO_INTR_DISABLE,
    };
    gpio_config(&btn_cfg);
}

} // namespace cafey

extern "C" void app_main(void) {
    using namespace cafey;

    // Inicializa driver do rele com protecao anti-glitch e garantia de repouso LOW
    drivers::Relay relay(PIN_RELAY);
    esp_err_t err = relay.init();
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "Falha critica ao inicializar driver do rele: %d", err);
    }

    // Carrega agendamentos e fila de eventos persistidos em NVS (spec §5.4):
    // sobrevivem a reboot e queda de energia. Consumidos futuramente pelos
    // Active Objects Agendador e Conectividade.
    static storage::ScheduleStore schedule_store;
    err = schedule_store.init();
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "Falha ao carregar agendamentos de NVS: %d", err);
    } else {
        ESP_LOGI(TAG, "agendamentos carregados de NVS: %d", static_cast<int>(schedule_store.count()));
    }

    static storage::EventQueueStore event_queue_store;
    err = event_queue_store.init();
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "Falha ao carregar fila de eventos de NVS: %d", err);
    } else {
        ESP_LOGI(TAG, "eventos pendentes carregados de NVS: %d", static_cast<int>(event_queue_store.size()));
    }

    // Inicializa pinos de LED e botao
    init_gpio();

    // Boot: LED azul por 1 segundo, rele garantidamente aberto
    led_set(false, false, true);
    ESP_LOGI(TAG, "boot - Cafey IoT C++ Firmware iniciado");
    vTaskDelay(pdMS_TO_TICKS(BOOT_BLINK_MS));

    // Estado pronto inicial: cafeteira desligada (LED vermelho)
    relay.turn_off();
    led_set(true, false, false);
    ESP_LOGI(TAG, "pronto - cafeteira DESLIGADA");

    int reading = gpio_get_level(PIN_BUTTON);
    int stable = reading;
    int same_count = 0;

    while (true) {
        int level = gpio_get_level(PIN_BUTTON);

        if (level != reading) {
            reading = level;
            same_count = 0;
        } else if (same_count < DEBOUNCE_SAMPLES) {
            same_count++;
            if (same_count == DEBOUNCE_SAMPLES && level != stable) {
                stable = level;
                if (stable == 0) { // Borda de descida = clique no botao (pull-up ativo)
                    relay.toggle();
                    bool is_on = relay.is_on();
                    // Vermelho = desligado, Verde = ligado
                    led_set(!is_on, is_on, false);
                    ESP_LOGI(TAG, "botao -> cafeteira %s", is_on ? "LIGADA" : "DESLIGADA");
                }
            }
        }
        vTaskDelay(pdMS_TO_TICKS(DEBOUNCE_POLL_MS));
    }
}
