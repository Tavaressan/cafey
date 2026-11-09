#include <cassert>
#include <iostream>

#include "app/coffee_command.hpp"

using cafey::app::CoffeeAction;
using cafey::app::CoffeeCommand;
using cafey::app::parse_coffee_command;

static void test_parses_each_action() {
    assert(parse_coffee_command(R"({"acao":"LIGAR"})").action == CoffeeAction::Ligar);
    assert(parse_coffee_command(R"({"acao":"DESLIGAR"})").action == CoffeeAction::Desligar);
    assert(parse_coffee_command(R"({"acao":"CANCELAR"})").action == CoffeeAction::Cancelar);
    std::cout << "OK: test_parses_each_action\n";
}

static void test_parses_optional_duration() {
    const CoffeeCommand with = parse_coffee_command(
        R"({"comandoId":"abc","acao":"LIGAR","duracaoS":420,"emitidoEm":"2026-09-01T13:00:00Z"})");
    assert(with.action == CoffeeAction::Ligar);
    assert(with.has_duracao);
    assert(with.duracao_s == 420);

    const CoffeeCommand without = parse_coffee_command(R"({"acao":"LIGAR"})");
    assert(!without.has_duracao);
    assert(without.duracao_s == 0);
    std::cout << "OK: test_parses_optional_duration\n";
}

static void test_unknown_or_missing_action_is_unknown() {
    assert(parse_coffee_command(R"({"acao":"EXPLODIR"})").action == CoffeeAction::Unknown);
    assert(parse_coffee_command(R"({"duracaoS":300})").action == CoffeeAction::Unknown);
    assert(parse_coffee_command("").action == CoffeeAction::Unknown);
    std::cout << "OK: test_unknown_or_missing_action_is_unknown\n";
}

static void test_parses_wifi_actions_fw30() {
    const CoffeeCommand forget = parse_coffee_command(
        R"({"acao":"ESQUECER_REDE","ssid":"MinhaRede"})");
    assert(forget.action == CoffeeAction::EsquecerRede);
    assert(forget.ssid == "MinhaRede");

    const CoffeeCommand forget_all = parse_coffee_command(R"({"acao":"ESQUECER_TODAS_REDES"})");
    assert(forget_all.action == CoffeeAction::EsquecerTodasRedes);
    std::cout << "OK: test_parses_wifi_actions_fw30\n";
}

int main() {
    test_parses_each_action();
    test_parses_optional_duration();
    test_unknown_or_missing_action_is_unknown();
    test_parses_wifi_actions_fw30();
    std::cout << "Todos os testes de parse_coffee_command passaram.\n";
    return 0;
}
