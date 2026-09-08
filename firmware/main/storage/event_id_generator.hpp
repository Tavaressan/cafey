#pragma once

#include <cstddef>
#include <cstdint>

#include "nvs_store.hpp"

namespace cafey::storage {

/**
 * @brief Gera identificadores de evento no formato `bootId:seq`
 * (spec-backend §3 / P8).
 *
 * `bootId` e fixo durante um boot; `seq` e monotonico e persistido em NVS,
 * de modo que a chave de deduplicacao nao se repete entre reinicios. Vive
 * numa chave/namespace NVS proprios, separado da fila de eventos (FW-06).
 */
class EventIdGenerator {
public:
    // Comprimento maximo do id gerado, incluindo o terminador nulo:
    // 8 (bootId em hex) + 1 (':') + 10 (uint32 decimal) + 1 = 20.
    static constexpr size_t kMaxIdLen = 20;

    explicit EventIdGenerator(const char* nvs_namespace = "evtid");

    /**
     * @brief Abre o NVS, carrega `seq` (0 se ausente) e fixa o `bootId` desta
     * sessao.
     * @return ESP_OK em sucesso, ou codigo de erro do ESP-IDF.
     */
    esp_err_t init(uint32_t boot_id);

    /**
     * @brief Escreve o proximo id em `out`, incrementa `seq` e persiste
     * imediatamente (o valor gravado fica a frente do ultimo id emitido, para
     * que um corte de energia nunca reaproveite um `seq`).
     * @param out Buffer de destino, ao menos `kMaxIdLen` bytes.
     * @return ESP_OK; ESP_ERR_INVALID_ARG se `out` for nulo ou pequeno demais;
     * ESP_ERR_INVALID_STATE se nao inicializado; ou erro do NVS.
     */
    esp_err_t next(char* out, size_t out_size);

    [[nodiscard]] uint32_t current_seq() const noexcept { return seq_; }
    [[nodiscard]] bool is_initialized() const noexcept { return initialized_; }

private:
    static constexpr const char* kSeqKey = "seq";

    NvsStore store_;
    uint32_t boot_id_ = 0;
    uint32_t seq_ = 0;
    bool initialized_ = false;
};

} // namespace cafey::storage
