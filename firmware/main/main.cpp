/*
 * Cafey - Firmware do modulo IoT (ESP-IDF C++17)
 *
 * Estrutura de Active Objects sobre FreeRTOS (spec 5.2 / FW-02):
 * cada componente e uma task com fila propria e maquina de estados interna.
 * Sem memoria compartilhada — comunicacao so por eventos assincronos.
 *
 * Pinagem (conforme STATUS.md e esquematico KiCad):
 * - Rele (GPIO26): gatilho de nivel ALTO, R1 pull-down 10k externo
 * - Botao (GPIO27): pull-up externo R2 10k + C3 100nF, solto=1, pressionado=0
 * - LED RGB: catodo comum (R=21, G=22, B=23)
 */

#include "esp_log.h"

#include "app/agendador.hpp"
#include "app/cafeteira.hpp"
#include "app/conectividade.hpp"

namespace {
const char* TAG = "cafey";
}

// AOs com duracao de armazenamento estatica: vivem por toda a execucao.
cafey::app::Cafeteira g_cafeteira;
cafey::app::Conectividade g_conectividade;
cafey::app::Agendador g_agendador;

extern "C" void app_main(void) {
    ESP_LOGI(TAG, "boot - Cafey IoT (Active Objects)");

    g_cafeteira.start();
    g_conectividade.start();
    g_agendador.start();

    ESP_LOGI(TAG, "pronto - AOs Cafeteira/Conectividade/Agendador iniciados");
}
