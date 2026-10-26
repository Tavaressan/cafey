#include "csr_generator.hpp"

#include <cstdio>
#include <cstring>
#include <vector>

#if defined(ESP_PLATFORM)
#include "esp_log.h"
#include "esp_console.h"
#include "mbedtls/pk.h"
#include "mbedtls/ecp.h"
#include "mbedtls/x509_csr.h"
#include "mbedtls/entropy.h"
#include "mbedtls/ctr_drbg.h"
#include "mbedtls/error.h"
#endif

namespace cafey::core {

static const char* TAG = "CsrGen";

#if defined(ESP_PLATFORM)
static int console_factory_csr_cmd(int argc, char** argv) {
    const char* cn = (argc > 1) ? argv[1] : "cafey-device";
    esp_err_t err = CsrGenerator::print_csr_to_serial(cn);
    return (err == ESP_OK) ? 0 : 1;
}
#endif

esp_err_t CsrGenerator::ensure_private_key() {
#if defined(ESP_PLATFORM)
    nvs_handle_t handle;
    esp_err_t err = nvs_open(kNamespace, NVS_READWRITE, &handle);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "Falha ao abrir NVS namespace %s: %s", kNamespace, esp_err_to_name(err));
        return err;
    }

    size_t needed = 0;
    err = nvs_get_str(handle, kKeyPrivateKey, nullptr, &needed);
    if (err == ESP_OK && needed > 0) {
        ESP_LOGI(TAG, "Chave privada ja existente na NVS.");
        nvs_close(handle);
        return ESP_OK;
    }

    ESP_LOGI(TAG, "Gerando nova chave ECC P-256 via mbedTLS...");
    mbedtls_pk_context pk;
    mbedtls_entropy_context entropy;
    mbedtls_ctr_drbg_context ctr_drbg;

    mbedtls_pk_init(&pk);
    mbedtls_entropy_init(&entropy);
    mbedtls_ctr_drbg_init(&ctr_drbg);

    const char* pers = "cafey_hw_csr_ecc";
    int ret = mbedtls_ctr_drbg_seed(&ctr_drbg, mbedtls_entropy_func, &entropy,
                                    reinterpret_cast<const unsigned char*>(pers), strlen(pers));
    if (ret != 0) {
        ESP_LOGE(TAG, "Falha mbedtls_ctr_drbg_seed: -0x%04x", -ret);
        nvs_close(handle);
        return ESP_FAIL;
    }

    ret = mbedtls_pk_setup(&pk, mbedtls_pk_info_from_type(MBEDTLS_PK_ECKEY));
    if (ret != 0) {
        ESP_LOGE(TAG, "Falha mbedtls_pk_setup: -0x%04x", -ret);
        nvs_close(handle);
        return ESP_FAIL;
    }

    ret = mbedtls_ecp_gen_key(MBEDTLS_ECP_DP_SECP256R1, mbedtls_pk_ec(pk),
                              mbedtls_ctr_drbg_random, &ctr_drbg);
    if (ret != 0) {
        ESP_LOGE(TAG, "Falha mbedtls_ecp_gen_key: -0x%04x", -ret);
        nvs_close(handle);
        return ESP_FAIL;
    }

    unsigned char key_pem[1024];
    memset(key_pem, 0, sizeof(key_pem));
    ret = mbedtls_pk_write_key_pem(&pk, key_pem, sizeof(key_pem));
    mbedtls_pk_free(&pk);
    mbedtls_ctr_drbg_free(&ctr_drbg);
    mbedtls_entropy_free(&entropy);

    if (ret != 0) {
        ESP_LOGE(TAG, "Falha mbedtls_pk_write_key_pem: -0x%04x", -ret);
        nvs_close(handle);
        return ESP_FAIL;
    }

    err = nvs_set_str(handle, kKeyPrivateKey, reinterpret_cast<char*>(key_pem));
    if (err == ESP_OK) {
        err = nvs_commit(handle);
        ESP_LOGI(TAG, "Chave privada ECC persistida com sucesso na NVS.");
    }
    nvs_close(handle);
    return err;
#else
    return ESP_OK;
#endif
}

esp_err_t CsrGenerator::generate_csr(const char* common_name, char* output_pem, size_t max_len) {
    if (!common_name || !output_pem || max_len == 0) return ESP_ERR_INVALID_ARG;
    esp_err_t err = ensure_private_key();
    if (err != ESP_OK) return err;

#if defined(ESP_PLATFORM)
    nvs_handle_t handle;
    err = nvs_open(kNamespace, NVS_READONLY, &handle);
    if (err != ESP_OK) return err;

    size_t needed = 0;
    err = nvs_get_str(handle, kKeyPrivateKey, nullptr, &needed);
    if (err != ESP_OK || needed == 0) {
        nvs_close(handle);
        return ESP_ERR_NOT_FOUND;
    }

    std::vector<char> key_pem(needed);
    err = nvs_get_str(handle, kKeyPrivateKey, key_pem.data(), &needed);
    nvs_close(handle);
    if (err != ESP_OK) return err;

    mbedtls_pk_context pk;
    mbedtls_entropy_context entropy;
    mbedtls_ctr_drbg_context ctr_drbg;
    mbedtls_x509write_csr csr;

    mbedtls_pk_init(&pk);
    mbedtls_entropy_init(&entropy);
    mbedtls_ctr_drbg_init(&ctr_drbg);
    mbedtls_x509write_csr_init(&csr);

    const char* pers = "cafey_csr_req";
    int ret = mbedtls_ctr_drbg_seed(&ctr_drbg, mbedtls_entropy_func, &entropy,
                                    reinterpret_cast<const unsigned char*>(pers), strlen(pers));
    if (ret != 0) return ESP_FAIL;

    ret = mbedtls_pk_parse_key(&pk, reinterpret_cast<const unsigned char*>(key_pem.data()),
                               strlen(key_pem.data()) + 1, nullptr, 0,
                               mbedtls_ctr_drbg_random, &ctr_drbg);
    if (ret != 0) {
        ESP_LOGE(TAG, "Falha ao fazer parse da chave privada: -0x%04x", -ret);
        mbedtls_pk_free(&pk);
        return ESP_FAIL;
    }

    mbedtls_x509write_csr_set_md_alg(&csr, MBEDTLS_MD_SHA256);
    mbedtls_x509write_csr_set_key(&csr, &pk);

    char subject[128];
    snprintf(subject, sizeof(subject), "CN=%s", common_name);
    ret = mbedtls_x509write_csr_set_subject_name(&csr, subject);
    if (ret != 0) {
        mbedtls_pk_free(&pk);
        mbedtls_x509write_csr_free(&csr);
        return ESP_FAIL;
    }

    memset(output_pem, 0, max_len);
    ret = mbedtls_x509write_csr_pem(&csr, reinterpret_cast<unsigned char*>(output_pem), max_len,
                                    mbedtls_ctr_drbg_random, &ctr_drbg);

    mbedtls_pk_free(&pk);
    mbedtls_x509write_csr_free(&csr);
    mbedtls_ctr_drbg_free(&ctr_drbg);
    mbedtls_entropy_free(&entropy);

    return (ret == 0) ? ESP_OK : ESP_FAIL;
#else
    snprintf(output_pem, max_len,
             "-----BEGIN CERTIFICATE REQUEST-----\n"
             "MOCK_CSR_FOR_%s\n"
             "-----END CERTIFICATE REQUEST-----\n", common_name);
    return ESP_OK;
#endif
}

esp_err_t CsrGenerator::print_csr_to_serial(const char* common_name) {
    char csr_buf[2048];
    esp_err_t err = generate_csr(common_name, csr_buf, sizeof(csr_buf));
    if (err != ESP_OK) return err;

    printf("\n=== BEGIN CAFEY FACTORY CSR ===\n");
    printf("%s", csr_buf);
    printf("=== END CAFEY FACTORY CSR ===\n");
    fflush(stdout);
    return ESP_OK;
}

void CsrGenerator::register_console_command() {
#if defined(ESP_PLATFORM)
    const esp_console_cmd_t cmd = {
        .command = "factory_csr",
        .help = "Gera chave ECC se ausente e imprime o CSR PEM para provisionamento de fábrica",
        .hint = "<uuid-ou-common-name>",
        .func = &console_factory_csr_cmd,
        .argtable = nullptr,
    };
    esp_console_cmd_register(&cmd);
#endif
}

} // namespace cafey::core
