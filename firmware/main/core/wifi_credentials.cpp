#include "wifi_credentials.hpp"

#include <algorithm>
#include <array>
#include <cstring>

namespace cafey::core {

void WifiCredentialsStore::copy_cstr(char* dst, size_t dst_len, const std::string& src) {
    if (dst_len == 0) return;
    std::memset(dst, 0, dst_len);
    if (src.empty()) return;
    const size_t n = std::min(src.size(), dst_len - 1);
    std::memcpy(dst, src.data(), n);
    dst[n] = '\0';
}

void WifiCredentialsStore::normalize_list(ListBlob& list) {
    if (list.version != kListVersion) {
        list.version = kListVersion;
        list.count = 0;
    }
    if (list.count > kMaxNetworks) {
        list.count = static_cast<uint8_t>(kMaxNetworks);
    }
    for (size_t i = 0; i < kMaxNetworks; ++i) {
        list.entries[i].ssid[kSsidMaxLen] = '\0';
        list.entries[i].password[kPasswordMaxLen] = '\0';
    }
}

esp_err_t WifiCredentialsStore::migrate_legacy_if_needed(ListBlob& list) const {
    // Chamado quando a chave nova ("list") nao existe. Verifica o formato antigo
    // (ssid/password) e, se presente, migra sem perder a rede gravada.
    nvs_handle_t handle;
    esp_err_t err = nvs_open(kNamespace, NVS_READONLY, &handle);
    if (err != ESP_OK) {
        // Namespace inexistente: nenhuma credencial gravada.
        list.version = kListVersion;
        list.count = 0;
        return ESP_ERR_NVS_NOT_FOUND;
    }

    std::array<char, kSsidMaxLen + 1> ssid_buf{};
    std::array<char, kPasswordMaxLen + 1> pass_buf{};
    size_t ssid_len = ssid_buf.size();
    size_t pass_len = pass_buf.size();

    const esp_err_t ssid_err = nvs_get_str(handle, kLegacySsidKey, ssid_buf.data(), &ssid_len);
    esp_err_t pass_err = ESP_ERR_NVS_NOT_FOUND;
    if (ssid_err == ESP_OK) {
        pass_err = nvs_get_str(handle, kLegacyPassKey, pass_buf.data(), &pass_len);
    }
    nvs_close(handle);

    if (ssid_err != ESP_OK) {
        list.version = kListVersion;
        list.count = 0;
        return ESP_ERR_NVS_NOT_FOUND;
    }

    // Formato antigo encontrado: migra para a lista (senha vazia se ausente).
    list = {};
    list.version = kListVersion;
    list.count = 1;
    copy_cstr(list.entries[0].ssid, sizeof(list.entries[0].ssid), ssid_buf.data());
    if (pass_err == ESP_OK) {
        copy_cstr(list.entries[0].password, sizeof(list.entries[0].password), pass_buf.data());
    }

    // Persiste no formato novo e remove as chaves antigas.
    nvs_handle_t wh;
    err = nvs_open(kNamespace, NVS_READWRITE, &wh);
    if (err != ESP_OK) {
        // Migracao em memoria: a rede continua acessivel nesta leitura; a
        // persistencia do novo formato sera tentada no proximo record_success.
        return ESP_OK;
    }
    err = nvs_set_blob(wh, kListKey, &list, sizeof(list));
    if (err == ESP_OK) {
        nvs_erase_key(wh, kLegacySsidKey);
        nvs_erase_key(wh, kLegacyPassKey);
        err = nvs_commit(wh);
    }
    nvs_close(wh);
    return (err == ESP_OK) ? ESP_OK : ESP_OK; // rede migrada em memoria
}

esp_err_t WifiCredentialsStore::load_list(ListBlob& out) const {
    out = {};
    out.version = kListVersion;
    out.count = 0;

    nvs_handle_t handle;
    esp_err_t err = nvs_open(kNamespace, NVS_READONLY, &handle);
    if (err != ESP_OK) {
        return migrate_legacy_if_needed(out);
    }

    size_t len = sizeof(out);
    err = nvs_get_blob(handle, kListKey, &out, &len);
    nvs_close(handle);

    if (err == ESP_ERR_NVS_NOT_FOUND) {
        return migrate_legacy_if_needed(out);
    }
    if (err != ESP_OK) {
        return err;
    }
    if (len != sizeof(ListBlob)) {
        // Tamanho inesperado (versao/layout incompativel): trata como vazio para
        // nao corromper estado; o proximo record_success reescreve o formato atual.
        out = {};
        out.version = kListVersion;
        out.count = 0;
        return ESP_ERR_NVS_NOT_FOUND;
    }
    normalize_list(out);
    if (out.count == 0) {
        return ESP_ERR_NVS_NOT_FOUND;
    }
    return ESP_OK;
}

esp_err_t WifiCredentialsStore::save_list(const ListBlob& list) const {
    nvs_handle_t handle;
    esp_err_t err = nvs_open(kNamespace, NVS_READWRITE, &handle);
    if (err != ESP_OK) {
        return err;
    }

    err = nvs_set_blob(handle, kListKey, &list, sizeof(ListBlob));
    if (err == ESP_OK) {
        // Remove chaves do formato antigo, se ainda existirem.
        nvs_erase_key(handle, kLegacySsidKey);
        nvs_erase_key(handle, kLegacyPassKey);
        err = nvs_commit(handle);
    }
    nvs_close(handle);
    return err;
}

esp_err_t WifiCredentialsStore::record_success(const std::string& ssid, const std::string& password) {
    if (ssid.empty()) {
        return ESP_ERR_INVALID_ARG;
    }

    ListBlob list{};
    list.version = kListVersion;
    // Ignora erro: lista vazia e um ponto de partida valido (ex.: primeira rede).
    (void)load_list(list);
    if (list.version != kListVersion) {
        list = {};
        list.version = kListVersion;
    }

    // Remove entrada existente com o mesmo SSID (atualizacao de senha/promocao).
    size_t write = 0;
    for (size_t i = 0; i < list.count && i < kMaxNetworks; ++i) {
        if (ssid == list.entries[i].ssid) {
            continue;
        }
        if (write != i) {
            list.entries[write] = list.entries[i];
        }
        ++write;
    }
    list.count = static_cast<uint8_t>(write);

    // Insere no topo (MRU). Se cheio, o ultimo (mais antigo) sai.
    const size_t keep = std::min<size_t>(list.count, kMaxNetworks - 1);
    for (size_t i = keep; i > 0; --i) {
        list.entries[i] = list.entries[i - 1];
    }
    copy_cstr(list.entries[0].ssid, sizeof(list.entries[0].ssid), ssid);
    copy_cstr(list.entries[0].password, sizeof(list.entries[0].password), password);
    list.count = static_cast<uint8_t>(keep + 1);

    return save_list(list);
}

esp_err_t WifiCredentialsStore::load(std::string& ssid, std::string& password) const {
    ListBlob list{};
    esp_err_t err = load_list(list);
    if (err != ESP_OK || list.count == 0) {
        return ESP_ERR_NVS_NOT_FOUND;
    }
    ssid = list.entries[0].ssid;
    password = list.entries[0].password;
    return ESP_OK;
}

esp_err_t WifiCredentialsStore::get(size_t index, std::string& ssid, std::string& password) const {
    ListBlob list{};
    esp_err_t err = load_list(list);
    if (err != ESP_OK || index >= list.count) {
        return ESP_ERR_NVS_NOT_FOUND;
    }
    ssid = list.entries[index].ssid;
    password = list.entries[index].password;
    return ESP_OK;
}

std::vector<WifiNetworkInfo> WifiCredentialsStore::list() const {
    std::vector<WifiNetworkInfo> out;
    ListBlob blob{};
    if (load_list(blob) != ESP_OK) {
        return out;
    }
    out.reserve(blob.count);
    for (size_t i = 0; i < blob.count; ++i) {
        WifiNetworkInfo info;
        info.ssid = blob.entries[i].ssid;
        out.push_back(std::move(info));
    }
    return out;
}

size_t WifiCredentialsStore::count() const {
    ListBlob list{};
    if (load_list(list) != ESP_OK) {
        return 0;
    }
    return list.count;
}

bool WifiCredentialsStore::has_credentials() const {
    return count() > 0;
}

esp_err_t WifiCredentialsStore::forget(const std::string& ssid) {
    ListBlob list{};
    if (load_list(list) != ESP_OK || list.count == 0) {
        return ESP_OK;
    }

    size_t write = 0;
    bool changed = false;
    for (size_t i = 0; i < list.count; ++i) {
        if (ssid == list.entries[i].ssid) {
            changed = true;
            continue;
        }
        if (write != i) {
            list.entries[write] = list.entries[i];
        }
        ++write;
    }
    if (!changed) {
        return ESP_OK;
    }
    // Zera entradas liberadas.
    for (size_t i = write; i < kMaxNetworks; ++i) {
        list.entries[i] = {};
    }
    list.count = static_cast<uint8_t>(write);

    if (list.count == 0) {
        return clear();
    }
    return save_list(list);
}

esp_err_t WifiCredentialsStore::clear() {
    nvs_handle_t handle;
    esp_err_t err = nvs_open(kNamespace, NVS_READWRITE, &handle);
    if (err != ESP_OK) {
        return err;
    }

    nvs_erase_key(handle, kListKey);
    nvs_erase_key(handle, kLegacySsidKey);
    nvs_erase_key(handle, kLegacyPassKey);
    err = nvs_commit(handle);
    nvs_close(handle);
    return err;
}

} // namespace cafey::core
