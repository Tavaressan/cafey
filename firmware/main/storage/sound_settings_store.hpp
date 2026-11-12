#pragma once

#include "nvs_store.hpp"

namespace cafey::storage {

/**
 * @brief Preferencia de som do modulo persistida em NVS (#311).
 *
 * Hoje guarda so o modo silencioso. Vive num namespace proprio para nao
 * misturar com agendamentos e fila de eventos. O controle pelo app fica para
 * #322; por enquanto chega pelos comandos `SILENCIAR` / `ATIVAR_SOM`.
 */
class SoundSettingsStore {
public:
    explicit SoundSettingsStore(const char* nvs_namespace = "cafey_som");

    /**
     * @brief Abre o NVS e carrega a preferencia (som ligado se ausente).
     * @return ESP_OK em sucesso, ou codigo de erro do ESP-IDF.
     */
    esp_err_t init();

    /** @brief Grava o modo silencioso e confirma na hora (sobrevive a reboot). */
    esp_err_t set_muted(bool muted);

    [[nodiscard]] bool muted() const noexcept { return muted_; }
    [[nodiscard]] bool is_initialized() const noexcept { return initialized_; }

private:
    static constexpr const char* kMutedKey = "mudo";

    NvsStore store_;
    bool muted_ = false;
    bool initialized_ = false;
};

} // namespace cafey::storage
