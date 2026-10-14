#include "app/timezone_offset.hpp"

#include <cstring>

namespace cafey::app {

namespace {

struct ZoneOffset {
    const char* iana;
    int32_t offset_s;
};

constexpr int32_t kHour = 3600;

constexpr ZoneOffset kZones[] = {
    {"America/Noronha", -2 * kHour},
    {"America/Sao_Paulo", -3 * kHour},
    {"America/Bahia", -3 * kHour},
    {"America/Fortaleza", -3 * kHour},
    {"America/Recife", -3 * kHour},
    {"America/Maceio", -3 * kHour},
    {"America/Belem", -3 * kHour},
    {"America/Araguaina", -3 * kHour},
    {"America/Santarem", -3 * kHour},
    {"America/Manaus", -4 * kHour},
    {"America/Cuiaba", -4 * kHour},
    {"America/Campo_Grande", -4 * kHour},
    {"America/Porto_Velho", -4 * kHour},
    {"America/Boa_Vista", -4 * kHour},
    {"America/Rio_Branco", -5 * kHour},
    {"America/Eirunepe", -5 * kHour},
    {"UTC", 0},
    {"Etc/UTC", 0},
};

} // namespace

bool utc_offset_s(const std::string& iana, int32_t* out) {
    if (out == nullptr) return false;
    for (const ZoneOffset& zone : kZones) {
        if (std::strcmp(zone.iana, iana.c_str()) == 0) {
            *out = zone.offset_s;
            return true;
        }
    }
    return false;
}

} // namespace cafey::app
