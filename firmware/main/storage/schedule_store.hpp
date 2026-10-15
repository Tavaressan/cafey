#pragma once

#include <cstddef>
#include <cstdint>

#include "schedule.hpp"
#include "nvs_store.hpp"

namespace cafey::storage {

/**
 * @brief Persists the device's coffee-maker schedules in NVS (spec §5.4:
 * "Agendamentos gravados em NVS — sobrevivem a reboot e queda de energia").
 *
 * Fixed-capacity array persisted as a single blob under one NVS key: simple
 * and sufficient for the expected schedule count (a handful of alarms per
 * device), avoiding a variable-length serialization format (YAGNI).
 */
class ScheduleStore {
public:
    static constexpr size_t kMaxSchedules = 16;
    // `timezone` IANA da lista, com terminador; 50 = tamanho da coluna no backend.
    static constexpr size_t kTimezoneLength = 50;

    ScheduleStore();

    /**
     * @brief Opens the underlying NVS namespace and loads any previously
     * persisted schedules into memory.
     * @return ESP_OK on success, or ESP-IDF error code.
     */
    esp_err_t init();

    /**
     * @brief Replaces the full schedule list and persists it immediately.
     * @return ESP_OK on success, ESP_ERR_INVALID_SIZE if `count` exceeds
     * kMaxSchedules, or ESP-IDF error code from the NVS write.
     */
    esp_err_t replace_all(const Schedule* schedules, size_t count, const char* timezone = "");

    /**
     * @brief Aplica a lista somente se `version` for maior que a gravada em NVS
     * (regra de versao monotonica, spec-backend §6.2). Protege contra a mensagem
     * retida antiga que o AWS IoT Core reentrega na reconexao.
     *
     * `version <= version()` -> lista ignorada por completo: agendamentos e
     * versao permanecem intactos e `*applied` recebe false. Caso contrario a
     * lista e a nova versao sao persistidas e `*applied` recebe true.
     *
     * @return ESP_ERR_INVALID_SIZE se `count` exceder kMaxSchedules; ESP_OK
     * quando a lista e aceita ou deliberadamente ignorada; ou o codigo de erro
     * da escrita em NVS.
     */
    esp_err_t replace_all_if_newer(const Schedule* schedules, size_t count,
                                   uint64_t version, bool* applied = nullptr,
                                   const char* timezone = "");

    [[nodiscard]] size_t count() const noexcept { return count_; }
    [[nodiscard]] const Schedule& at(size_t index) const { return schedules_[index]; }

    /** Versao da lista de agendamentos atualmente gravada (0 = nada gravado). */
    [[nodiscard]] uint64_t version() const noexcept { return version_; }

    /** Fuso IANA gravado junto da lista (FW-23); vazio se nada foi gravado. */
    [[nodiscard]] const char* timezone() const noexcept { return timezone_; }

private:
    // Prefixo de schema gravado no início do blob (FW-19). Permite detectar um
    // blob de layout antigo (sem prefixo, tamanho diferente) ou corrompido e
    // tratá-lo como store vazio, deixando o backend republicar a lista retida.
    static constexpr uint32_t kScheduleMagic = 0x53464143;   // 'CAFS' LE
    // v2: Schedule::duration_s (#200) e timezone (FW-23). O timezone entrou no
    // v2 sem subir a versao porque nenhuma placa chegou a gravar um blob v2.
    static constexpr uint16_t kScheduleSchemaVersion = 2;

    struct PersistedLayout {
        uint32_t magic;
        uint16_t schema_version;
        uint16_t reserved;
        uint64_t version;
        uint32_t count;
        char timezone[kTimezoneLength];
        Schedule schedules[kMaxSchedules];
    };

    NvsStore nvs_;
    Schedule schedules_[kMaxSchedules];
    size_t count_;
    uint64_t version_ = 0;
    char timezone_[kTimezoneLength] = {0};
};

} // namespace cafey::storage
