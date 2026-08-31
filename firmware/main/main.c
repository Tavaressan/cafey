/*
 * Cafey - firmware do modulo IoT (fatia de I/O local, sem rede)
 *
 * Botao (GPIO27) alterna o rele (GPIO26, gatilho de nivel ALTO).
 * LED RGB de catodo comum indica o estado:
 *   azul     -> boot
 *   vermelho -> cafeteira desligada (rele aberto)
 *   verde    -> cafeteira ligada (rele fechado)
 *
 * Pinagem conforme STATUS.md e o esquematico KiCad.
 */

#include <stdbool.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "driver/gpio.h"
#include "esp_log.h"

#define PIN_RELAY   GPIO_NUM_26
#define PIN_BUTTON  GPIO_NUM_27
#define PIN_LED_R   GPIO_NUM_21
#define PIN_LED_G   GPIO_NUM_22
#define PIN_LED_B   GPIO_NUM_23

#define BOOT_BLINK_MS     1000
#define DEBOUNCE_POLL_MS  10
#define DEBOUNCE_SAMPLES  3   /* 3 x 10 ms = 30 ms de nivel estavel */

static const char *TAG = "cafey";

static void led_set(bool r, bool g, bool b)
{
    gpio_set_level(PIN_LED_R, r);
    gpio_set_level(PIN_LED_G, g);
    gpio_set_level(PIN_LED_B, b);
}

static void relay_set(bool on)
{
    gpio_set_level(PIN_RELAY, on);
}

void app_main(void)
{
    /* Saidas. O rele e forcado em nivel BAIXO antes de tudo para nao pulsar a
       carga durante o boot; o R1 externo ja segura o pino em BAIXO enquanto o
       GPIO esta em alta impedancia. */
    const gpio_config_t out_cfg = {
        .pin_bit_mask = (1ULL << PIN_RELAY) | (1ULL << PIN_LED_R) |
                        (1ULL << PIN_LED_G) | (1ULL << PIN_LED_B),
        .mode = GPIO_MODE_OUTPUT,
        .pull_up_en = GPIO_PULLUP_DISABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type = GPIO_INTR_DISABLE,
    };
    gpio_config(&out_cfg);
    relay_set(false);
    led_set(false, false, false);

    /* Entrada do botao. Ha R2 externo (pull-up para 3V3); o pull-up interno
       fica ligado como reforco. Solto = 1, pressionado = 0. */
    const gpio_config_t in_cfg = {
        .pin_bit_mask = (1ULL << PIN_BUTTON),
        .mode = GPIO_MODE_INPUT,
        .pull_up_en = GPIO_PULLUP_ENABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type = GPIO_INTR_DISABLE,
    };
    gpio_config(&in_cfg);

    /* Boot: azul por 1 s, rele garantidamente aberto. */
    led_set(false, false, true);
    ESP_LOGI(TAG, "boot");
    vTaskDelay(pdMS_TO_TICKS(BOOT_BLINK_MS));

    bool relay_on = false;
    relay_set(relay_on);
    led_set(true, false, false); /* vermelho = desligado */
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
                if (stable == 0) { /* borda de descida = clique */
                    relay_on = !relay_on;
                    relay_set(relay_on);
                    led_set(!relay_on, relay_on, false);
                    ESP_LOGI(TAG, "botao -> cafeteira %s",
                             relay_on ? "LIGADA" : "DESLIGADA");
                }
            }
        }
        vTaskDelay(pdMS_TO_TICKS(DEBOUNCE_POLL_MS));
    }
}
