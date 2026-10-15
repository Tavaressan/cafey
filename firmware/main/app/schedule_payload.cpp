#include "app/schedule_payload.hpp"

#include <algorithm>
#include <cstdlib>
#include <cstring>
#include <limits>
#include <vector>

#include "core/mini_json.hpp"

namespace cafey::app {

namespace mj = cafey::core::mini_json;

bool parse_schedule_payload(const std::string& json, cafey::storage::Schedule* out,
                            size_t max, size_t* out_count, SchedulePayloadMeta* meta) {
    if (out == nullptr || out_count == nullptr) {
        return false;
    }

    SchedulePayloadMeta parsed_meta{};
    long versao = 0;
    if (mj::get_long(json, "versao", versao) && versao >= 0) {
        parsed_meta.version = static_cast<uint32_t>(versao);
    }
    long duracao = 0;
    if (mj::get_long(json, "duracaoS", duracao) && duracao > 0) {
        parsed_meta.duracao_s = static_cast<uint32_t>(duracao);
        parsed_meta.has_duracao = true;
    }
    mj::get_string(json, "timezone", parsed_meta.timezone);

    const std::vector<std::string> items = mj::object_array(json, "agendamentos");
    size_t count = 0;
    for (const std::string& item : items) {
        if (count >= max) break;

        cafey::storage::Schedule schedule{};

        std::string id;
        if (mj::get_string(item, "id", id)) {
            std::memcpy(schedule.id, id.data(),
                        std::min(id.size(), cafey::storage::Schedule::kIdLength));
        }

        std::string hora;
        if (mj::get_string(item, "hora", hora)) {
            const size_t colon = hora.find(':');
            if (colon != std::string::npos) {
                schedule.hour = static_cast<uint8_t>(std::atoi(hora.substr(0, colon).c_str()));
                schedule.minute = static_cast<uint8_t>(std::atoi(hora.substr(colon + 1).c_str()));
            }
        }

        long dias = 0;
        if (mj::get_long(item, "diasSemana", dias)) {
            schedule.days_of_week = static_cast<uint8_t>(dias);
        }

        bool ativo = false;
        if (mj::get_bool(item, "ativo", ativo)) {
            schedule.active = ativo;
        }

        // #200: cada item traz a propria duracao (PR #203); sem ela, vale a
        // duracao padrao do dispositivo enviada no topo do payload.
        long item_duracao = 0;
        if (mj::get_long(item, "duracaoS", item_duracao) && item_duracao > 0) {
            schedule.duration_s = static_cast<uint16_t>(
                std::min<long>(item_duracao, std::numeric_limits<uint16_t>::max()));
        } else {
            schedule.duration_s = static_cast<uint16_t>(
                std::min<uint32_t>(parsed_meta.duracao_s, std::numeric_limits<uint16_t>::max()));
        }

        out[count++] = schedule;
    }

    *out_count = count;
    if (meta != nullptr) {
        *meta = parsed_meta;
    }
    return true;
}

} // namespace cafey::app
