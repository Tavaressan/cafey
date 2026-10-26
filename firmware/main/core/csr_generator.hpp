#pragma once

#include <cstddef>
#include <string>

#if defined(ESP_PLATFORM)
#include "esp_err.h"
#include "nvs.h"
#include "nvs_flash.h"
#else
#include "mock_esp_nvs.hpp"
#endif

namespace cafey::core {

/**
 * @brief Gera par de chaves ECC (NIST P-256) com mbedTLS e exporta CSR em PEM (INFRA-10, Issue #274).
 *
 * A chave privada e' gerada diretamente no hardware e persistida na NVS ('cafey_mqtt'/'key');
 * ela nunca sai do ESP32. O CSR gerado e' exportado pela serial para ser assinado pela AWS IoT.
 */
class CsrGenerator {
public:
    static constexpr const char* kNamespace = "cafey_mqtt";
    static constexpr const char* kKeyPrivateKey = "key";

    /**
     * @brief Gera o par de chaves ECC P-256 e persiste a chave privada na NVS,
     * caso ela ainda nao exista. Se ja existir, mantem a existente.
     * @return ESP_OK em sucesso.
     */
    static esp_err_t ensure_private_key();

    /**
     * @brief Cria um CSR em formato PEM assinado com a chave ECC existente.
     * @param common_name O CN a ser gravado no Subject do CSR (geralmente o UUID da Thing).
     * @param output_pem Buffer para armazenar o CSR em formato PEM.
     * @param max_len Tamanho maximo do buffer.
     * @return ESP_OK em sucesso.
     */
    static esp_err_t generate_csr(const char* common_name, char* output_pem, size_t max_len);

    /**
     * @brief Imprime o CSR pela serial com marcadores delimitadores legíveis por scripts.
     */
    static esp_err_t print_csr_to_serial(const char* common_name);

    /**
     * @brief Registra o comando de console "factory_csr" no console do ESP-IDF.
     */
    static void register_console_command();
};

} // namespace cafey::core
