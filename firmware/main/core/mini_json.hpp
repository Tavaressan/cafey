#pragma once

#include <cctype>
#include <cstddef>
#include <string>
#include <vector>

namespace cafey::core::mini_json {

/**
 * @brief Leitor JSON minimo por busca ingenua de `"key"`. Suficiente para os
 * payloads controlados e versionados de comando/agendamentos/eventos trocados
 * entre firmware e backend (spec-backend §6.2) — evita puxar uma dependencia de
 * parser completo so para tres formatos fixos (KISS/YAGNI).
 *
 * Nao valida JSON: assume o contrato compartilhado. A validacao real acontece
 * na fronteira de dominio (acao desconhecida e ignorada, etc.).
 */

inline bool is_ws(char c) { return c == ' ' || c == '\t' || c == '\n' || c == '\r'; }

/**
 * @brief Copia em `out` o valor bruto associado a `key` (sem as aspas externas,
 * no caso de string). Retorna false se a chave nao existir.
 */
inline bool raw_value(const std::string& json, const std::string& key, std::string& out) {
    const std::string needle = "\"" + key + "\"";
    size_t k = json.find(needle);
    if (k == std::string::npos) return false;
    size_t colon = json.find(':', k + needle.size());
    if (colon == std::string::npos) return false;
    size_t i = colon + 1;
    while (i < json.size() && is_ws(json[i])) ++i;
    if (i >= json.size()) return false;

    if (json[i] == '"') {
        size_t end = json.find('"', i + 1);
        if (end == std::string::npos) return false;
        out = json.substr(i + 1, end - i - 1);
        return true;
    }

    size_t end = i;
    while (end < json.size() && json[end] != ',' && json[end] != '}' && json[end] != ']') {
        ++end;
    }
    out = json.substr(i, end - i);
    while (!out.empty() && is_ws(out.back())) out.pop_back();
    return true;
}

inline bool get_string(const std::string& json, const std::string& key, std::string& out) {
    return raw_value(json, key, out);
}

inline bool get_long(const std::string& json, const std::string& key, long& out) {
    std::string v;
    if (!raw_value(json, key, v) || v.empty()) return false;
    // Parser manual: o ESP-IDF compila com -fno-exceptions, entao std::stol
    // (que lanca) nao pode ser usado. Le sinal opcional e os digitos iniciais,
    // ignorando o resto — mesmo comportamento tolerante do std::stol anterior.
    size_t i = 0;
    bool negative = false;
    if (v[i] == '+' || v[i] == '-') {
        negative = (v[i] == '-');
        ++i;
    }
    size_t digits = 0;
    long parsed = 0;
    while (i < v.size() && std::isdigit(static_cast<unsigned char>(v[i]))) {
        parsed = parsed * 10 + (v[i] - '0');
        ++i;
        ++digits;
    }
    if (digits == 0) return false;
    out = negative ? -parsed : parsed;
    return true;
}

inline bool get_bool(const std::string& json, const std::string& key, bool& out) {
    std::string v;
    if (!raw_value(json, key, v)) return false;
    if (v == "true") { out = true; return true; }
    if (v == "false") { out = false; return true; }
    return false;
}

/**
 * @brief Extrai o texto de cada objeto `{...}` do array associado a `key`,
 * respeitando o aninhamento de chaves.
 */
inline std::vector<std::string> object_array(const std::string& json, const std::string& key) {
    std::vector<std::string> result;
    const std::string needle = "\"" + key + "\"";
    size_t k = json.find(needle);
    if (k == std::string::npos) return result;
    size_t lb = json.find('[', k);
    if (lb == std::string::npos) return result;

    int depth = 0;
    size_t start = std::string::npos;
    for (size_t i = lb + 1; i < json.size(); ++i) {
        char ch = json[i];
        if (ch == ']' && depth == 0) break;
        if (ch == '{') {
            if (depth == 0) start = i;
            ++depth;
        } else if (ch == '}') {
            --depth;
            if (depth == 0 && start != std::string::npos) {
                result.push_back(json.substr(start, i - start + 1));
                start = std::string::npos;
            }
        }
    }
    return result;
}

/**
 * @brief Le um array de inteiros nao-negativos associado a `key`
 * (ex.: `"confirmados":[123,456]`).
 */
inline std::vector<unsigned long> uint_array(const std::string& json, const std::string& key) {
    std::vector<unsigned long> out;
    const std::string needle = "\"" + key + "\"";
    size_t k = json.find(needle);
    if (k == std::string::npos) return out;
    size_t lb = json.find('[', k);
    if (lb == std::string::npos) return out;
    size_t rb = json.find(']', lb);
    if (rb == std::string::npos) return out;

    const std::string body = json.substr(lb + 1, rb - lb - 1);
    size_t i = 0;
    while (i < body.size()) {
        while (i < body.size() && !std::isdigit(static_cast<unsigned char>(body[i]))) ++i;
        if (i >= body.size()) break;
        size_t j = i;
        unsigned long value = 0;
        while (j < body.size() && std::isdigit(static_cast<unsigned char>(body[j]))) {
            value = value * 10 + static_cast<unsigned long>(body[j] - '0');
            ++j;
        }
        out.push_back(value);
        i = j;
    }
    return out;
}

} // namespace cafey::core::mini_json
