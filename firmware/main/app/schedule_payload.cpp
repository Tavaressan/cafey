#include "app/schedule_payload.hpp"

#include <algorithm>
#include <cstdlib>
#include <cstring>
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

        out[count++] = schedule;
    }

    *out_count = count;
    if (meta != nullptr) {
        *meta = parsed_meta;
    }
    return true;
}

} // namespace cafey::app
