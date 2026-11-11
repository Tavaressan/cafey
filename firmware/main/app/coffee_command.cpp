#include "app/coffee_command.hpp"

#include "core/mini_json.hpp"

namespace cafey::app {

namespace mj = cafey::core::mini_json;

CoffeeCommand parse_coffee_command(const std::string& json) {
    CoffeeCommand cmd;

    std::string acao;
    if (mj::get_string(json, "acao", acao)) {
        if (acao == "LIGAR") {
            cmd.action = CoffeeAction::Ligar;
        } else if (acao == "DESLIGAR") {
            cmd.action = CoffeeAction::Desligar;
        } else if (acao == "CANCELAR") {
            cmd.action = CoffeeAction::Cancelar;
        } else if (acao == "ESQUECER_REDE") {
            cmd.action = CoffeeAction::EsquecerRede;
        } else if (acao == "ESQUECER_TODAS_REDES") {
            cmd.action = CoffeeAction::EsquecerTodasRedes;
        } else if (acao == "SILENCIAR") {
            cmd.action = CoffeeAction::Silenciar;
        } else if (acao == "ATIVAR_SOM") {
            cmd.action = CoffeeAction::AtivarSom;
        }
    }

    long dur = 0;
    if (mj::get_long(json, "duracaoS", dur) && dur > 0) {
        cmd.duracao_s = static_cast<uint32_t>(dur);
        cmd.has_duracao = true;
    }

    std::string ssid;
    if (mj::get_string(json, "ssid", ssid)) {
        cmd.ssid = ssid;
    }

    return cmd;
}

} // namespace cafey::app
