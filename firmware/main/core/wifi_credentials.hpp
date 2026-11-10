#pragma once

#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

#if defined(ESP_PLATFORM)
#include "esp_err.h"
#include "nvs.h"
#include "nvs_flash.h"
#else
#include "mock_esp_nvs.hpp"
#endif

namespace cafey::core {

struct WifiNetworkInfo {
    std::string ssid;
    // senha omitida: nunca expor em listagens/log/BLE
};

/**
 * @brief Persiste um historico de redes Wi-Fi provisionadas (SSID/senha) em NVS.
 *
 * Mantem ate kMaxNetworks redes em ordem de uso (a ultima conectada com sucesso
 * fica no topo). Provisionar uma rede conhecida atualiza a senha e a promove;
 * ao passar do limite, descarta a usada ha mais tempo. Migra o formato antigo
 * (chaves unicas ssid/password) na primeira leitura sem perder a rede gravada.
 *
 * Senhas permanecem apenas no NVS e nao devem ser logadas nem expostas via BLE.
 */
class WifiCredentialsStore {
public:
    static constexpr size_t kMaxNetworks = 5;
    static constexpr size_t kSsidMaxLen = 32;
    static constexpr size_t kPasswordMaxLen = 64;

    WifiCredentialsStore() = default;

    /**
     * @brief Registra uma conexao bem-sucedida: insere ou atualiza a rede e a
     * move para o topo (MRU). Ao exceder kMaxNetworks, remove a mais antiga.
     * Esta e a unica operacao que adiciona/altera a lista persistida.
     */
    esp_err_t record_success(const std::string& ssid, const std::string& password);

    /**
     * @brief Carrega a rede mais recentemente usada com sucesso (topo da lista).
     * @return ESP_OK em sucesso, ESP_ERR_NVS_NOT_FOUND se nenhuma rede gravada.
     */
    esp_err_t load(std::string& ssid, std::string& password) const;

    /** @brief Alias de load(): retorna a rede primaria (topo). */
    esp_err_t load_primary(std::string& ssid, std::string& password) const {
        return load(ssid, password);
    }

    /**
     * @brief Obtem a rede no indice (0 = mais recente). Inclui a senha para uso
     * interno de conexao; nao expor o resultado em log nem BLE.
     */
    esp_err_t get(size_t index, std::string& ssid, std::string& password) const;

    /** @brief Lista as redes conhecidas em ordem MRU, sem expor senhas. */
    std::vector<WifiNetworkInfo> list() const;

    /** @brief Numero de redes gravadas. */
    [[nodiscard]] size_t count() const;

    /** @brief true se ha ao menos uma rede gravada. */
    [[nodiscard]] bool has_credentials() const;

    /** @brief Remove uma rede pelo SSID. Retorna ESP_OK mesmo se ausente. */
    esp_err_t forget(const std::string& ssid);

    /** @brief Apaga todas as redes gravadas. */
    esp_err_t clear();

private:
    struct Entry {
        char ssid[kSsidMaxLen + 1];
        char password[kPasswordMaxLen + 1];
    };

    struct ListBlob {
        uint8_t version;
        uint8_t count;
        Entry entries[kMaxNetworks];
    };

    static constexpr const char* kNamespace = "cafey_wifi";
    static constexpr const char* kListKey = "list";
    static constexpr const char* kLegacySsidKey = "ssid";
    static constexpr const char* kLegacyPassKey = "password";
    static constexpr uint8_t kListVersion = 1;

    esp_err_t load_list(ListBlob& out) const;
    esp_err_t save_list(const ListBlob& list) const;
    esp_err_t migrate_legacy_if_needed(ListBlob& list) const;
    static void normalize_list(ListBlob& list);
    static void copy_cstr(char* dst, size_t dst_len, const std::string& src);
};

} // namespace cafey::core
