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
        }
    }

    long dur = 0;
    if (mj::get_long(json, "duracaoS", dur) && dur > 0) {
        cmd.duracao_s = static_cast<uint32_t>(dur);
        cmd.has_duracao = true;
    }

    return cmd;
}

} // namespace cafey::app
