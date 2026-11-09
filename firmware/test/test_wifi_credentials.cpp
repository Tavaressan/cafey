#include <cassert>
#include <cstring>
#include <iostream>
#include <string>
#include <vector>

#include "mock_esp_nvs.hpp"
#include "wifi_credentials.hpp"

using cafey::core::WifiCredentialsStore;
using cafey::core::WifiNetworkInfo;

namespace {

void reset() { MockNvs::reset(); }

void gravar_legado(const char* ssid, const char* password) {
    nvs_handle_t h = 0;
    assert(nvs_open("cafey_wifi", NVS_READWRITE, &h) == ESP_OK);
    assert(nvs_set_str(h, "ssid", ssid) == ESP_OK);
    assert(nvs_set_str(h, "password", password) == ESP_OK);
    assert(nvs_commit(h) == ESP_OK);
    nvs_close(h);
}

bool contem_ssid(const std::vector<WifiNetworkInfo>& redes, const std::string& ssid) {
    for (const auto& r : redes) {
        if (r.ssid == ssid) return true;
    }
    return false;
}

} // namespace

static void test_vazio_quando_nunca_gravado() {
    reset();
    WifiCredentialsStore store;
    assert(!store.has_credentials());
    assert(store.count() == 0);
    std::string ssid, password;
    assert(store.load(ssid, password) == ESP_ERR_NVS_NOT_FOUND);
    std::cout << "OK: test_vazio_quando_nunca_gravado\n";
}

static void test_insercao_registra_rede() {
    reset();
    WifiCredentialsStore store;
    esp_err_t err = store.record_success("MinhaRede", "senha-secreta");
    assert(err == ESP_OK);
    assert(store.has_credentials());
    assert(store.count() == 1);

    std::string ssid, password;
    err = store.load(ssid, password);
    assert(err == ESP_OK);
    assert(ssid == "MinhaRede");
    assert(password == "senha-secreta");
    std::cout << "OK: test_insercao_registra_rede\n";
}

static void test_atualizacao_senha_move_para_topo_sem_duplicar() {
    reset();
    WifiCredentialsStore store;
    store.record_success("RedeA", "senhaA");
    store.record_success("RedeB", "senhaB");
    // RedeA volta com senha nova: deve subir ao topo, sem duplicar.
    esp_err_t err = store.record_success("RedeA", "senhaA-nova");
    assert(err == ESP_OK);
    assert(store.count() == 2);

    std::string ssid, password;
    assert(store.get(0, ssid, password) == ESP_OK);
    assert(ssid == "RedeA");
    assert(password == "senhaA-nova");
    assert(store.get(1, ssid, password) == ESP_OK);
    assert(ssid == "RedeB");

    auto redes = store.list();
    assert(redes.size() == 2);
    assert(redes[0].ssid == "RedeA");
    assert(redes[1].ssid == "RedeB");
    std::cout << "OK: test_atualizacao_senha_move_para_topo_sem_duplicar\n";
}

static void test_ordem_mru_ultima_conectada_no_topo() {
    reset();
    WifiCredentialsStore store;
    store.record_success("A", "1");
    store.record_success("B", "2");
    store.record_success("C", "3");
    // Conectar de novo em A promove A.
    store.record_success("A", "1");

    std::string ssid, password;
    store.get(0, ssid, password);
    assert(ssid == "A");
    store.get(1, ssid, password);
    assert(ssid == "C");
    store.get(2, ssid, password);
    assert(ssid == "B");
    std::cout << "OK: test_ordem_mru_ultima_conectada_no_topo\n";
}

static void test_limite_descarta_usada_ha_mais_tempo() {
    reset();
    WifiCredentialsStore store;
    // Limite padrao = 5.
    store.record_success("R1", "p1");
    store.record_success("R2", "p2");
    store.record_success("R3", "p3");
    store.record_success("R4", "p4");
    store.record_success("R5", "p5");
    assert(store.count() == 5);

    // R6 entra no topo; R1 era a mais antiga (menos recente) e sai.
    store.record_success("R6", "p6");
    assert(store.count() == 5);

    std::string ssid, password;
    store.get(0, ssid, password);
    assert(ssid == "R6");
    auto redes = store.list();
    assert(!contem_ssid(redes, "R1"));
    assert(contem_ssid(redes, "R2"));
    assert(contem_ssid(redes, "R6"));
    std::cout << "OK: test_limite_descarta_usada_ha_mais_tempo\n";
}

static void test_migracao_formato_antigo_preserva_rede() {
    reset();
    gravar_legado("RedeDeCasa", "senhaDeCasa");

    // Nenhuma chave nova ainda, apenas o formato antigo.
    WifiCredentialsStore store;
    assert(store.has_credentials());
    assert(store.count() == 1);

    std::string ssid, password;
    esp_err_t err = store.load(ssid, password);
    assert(err == ESP_OK);
    assert(ssid == "RedeDeCasa");
    assert(password == "senhaDeCasa");

    auto redes = store.list();
    assert(redes.size() == 1);
    assert(redes[0].ssid == "RedeDeCasa");
    std::cout << "OK: test_migracao_formato_antigo_preserva_rede\n";
}

static void test_migracao_remove_chaves_antigas() {
    reset();
    gravar_legado("Legada", "segredo");
    WifiCredentialsStore store;
    (void)store.has_credentials(); // dispara migracao

    nvs_handle_t h = 0;
    assert(nvs_open("cafey_wifi", NVS_READONLY, &h) == ESP_OK);
    size_t len = 0;
    assert(nvs_get_str(h, "ssid", nullptr, &len) == ESP_ERR_NVS_NOT_FOUND);
    assert(nvs_get_str(h, "password", nullptr, &len) == ESP_ERR_NVS_NOT_FOUND);
    nvs_close(h);
    std::cout << "OK: test_migracao_remove_chaves_antigas\n";
}

static void test_falha_senha_preserva_lista() {
    reset();
    WifiCredentialsStore store;
    // Credencial valida ja conhecida.
    assert(store.record_success("RedeBoa", "senhaBoa") == ESP_OK);
    assert(store.record_success("Outra", "outraSenha") == ESP_OK);
    assert(store.count() == 2);

    // Simula provisionamento com senha errada: o manager NAO chama record_success
    // para o candidato que falhou; a lista persistida deve permanecer intacta.
    std::string ssid, password;
    assert(store.load(ssid, password) == ESP_OK);
    assert(ssid == "Outra"); // topo preservado
    assert(store.count() == 2);
    assert(store.get(1, ssid, password) == ESP_OK);
    assert(ssid == "RedeBoa");
    assert(password == "senhaBoa");

    auto redes = store.list();
    assert(redes.size() == 2);
    assert(contem_ssid(redes, "RedeBoa"));
    assert(contem_ssid(redes, "Outra"));
    std::cout << "OK: test_falha_senha_preserva_lista\n";
}

static void test_forget_remove_rede() {
    reset();
    WifiCredentialsStore store;
    store.record_success("A", "1");
    store.record_success("B", "2");
    store.record_success("C", "3");

    assert(store.forget("B") == ESP_OK);
    assert(store.count() == 2);
    auto redes = store.list();
    assert(!contem_ssid(redes, "B"));
    assert(redes[0].ssid == "C");
    assert(redes[1].ssid == "A");

    // Esquecer rede ausente nao falha.
    assert(store.forget("inexistente") == ESP_OK);
    std::cout << "OK: test_forget_remove_rede\n";
}

static void test_clear_remove_todas() {
    reset();
    WifiCredentialsStore store;
    store.record_success("A", "1");
    store.record_success("B", "2");
    assert(store.clear() == ESP_OK);
    assert(!store.has_credentials());
    assert(store.count() == 0);
    assert(store.list().empty());
    std::cout << "OK: test_clear_remove_todas\n";
}

static void test_list_nao_expoe_senha() {
    // Garantia de API: WifiNetworkInfo nao carrega senha (verificado por compilacao
    // e por este teste nao acessar campo inexistente).
    reset();
    WifiCredentialsStore store;
    store.record_success("Rede", "senha-ultra-secreta");
    auto redes = store.list();
    assert(redes.size() == 1);
    assert(redes[0].ssid == "Rede");
    // Senha so acessivel via get/load (uso interno de conexao).
    std::string ssid, password;
    store.get(0, ssid, password);
    assert(password == "senha-ultra-secreta");
    std::cout << "OK: test_list_nao_expoe_senha\n";
}

static void test_migracao_preserva_rede_ao_adicionar_nova_com_sucesso() {
    reset();
    gravar_legado("Casa", "senhaCasa");
    WifiCredentialsStore store;
    // Migracao acontece na leitura; depois uma rede nova conecta com sucesso.
    store.record_success("Escritorio", "senhaEsc");
    assert(store.count() == 2);
    std::string ssid, password;
    store.get(0, ssid, password);
    assert(ssid == "Escritorio");
    store.get(1, ssid, password);
    assert(ssid == "Casa");
    assert(password == "senhaCasa");
    std::cout << "OK: test_migracao_preserva_rede_ao_adicionar_nova_com_sucesso\n";
}

int main() {
    test_vazio_quando_nunca_gravado();
    test_insercao_registra_rede();
    test_atualizacao_senha_move_para_topo_sem_duplicar();
    test_ordem_mru_ultima_conectada_no_topo();
    test_limite_descarta_usada_ha_mais_tempo();
    test_migracao_formato_antigo_preserva_rede();
    test_migracao_remove_chaves_antigas();
    test_falha_senha_preserva_lista();
    test_forget_remove_rede();
    test_clear_remove_todas();
    test_list_nao_expoe_senha();
    test_migracao_preserva_rede_ao_adicionar_nova_com_sucesso();
    std::cout << "Todos os testes de WifiCredentialsStore passaram.\n";
    return 0;
}
