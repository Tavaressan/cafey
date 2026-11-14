#include "app/ambiente.hpp"

#if defined(ESP_PLATFORM)
#include "esp_log.h"
#endif

namespace cafey::app {

using cafey::drivers::Dht11;
using cafey::drivers::Dht11Reading;
using cafey::drivers::Dht11Status;

namespace {
const char* TAG = "ambiente";
}

Ambiente::Ambiente()
    : ActiveObject("ambiente", 4, /*stack_size=*/3072, /*priority=*/3, kSamplePeriodMs) {}

void Ambiente::attach_sensor(Dht11* sensor) {
    reader_ = [sensor](Dht11Reading& out) { return sensor->read(out); };
}

void Ambiente::on_tick() {
    if (!reader_) return;
    ++ticks_since_report_;

    Dht11Reading reading;
    const Dht11Status status = reader_(reading);

    if (status != Dht11Status::Ok) {
        if (failures_ < 255) ++failures_;
        ESP_LOGW(TAG, "leitura do DHT11 falhou (status %d, %u seguida(s))",
                 static_cast<int>(status), static_cast<unsigned>(failures_));
        if (failures_ >= kFailuresToFault && !fault_reported_) {
            fault_reported_ = true;
            last_ = Sample{};
            report(last_);
        }
        return;
    }

    failures_ = 0;
    const bool changed = !last_.valid || last_.temperature_dc != reading.temperature_dc ||
                         last_.humidity_pct != reading.humidity_pct;
    last_ = Sample{true, reading.temperature_dc, reading.humidity_pct};

    if (changed || fault_reported_ || ticks_since_report_ >= kReportEveryTicks) {
        fault_reported_ = false;
        report(last_);
    }
}

void Ambiente::report(const Sample& s) {
    ticks_since_report_ = 0;
    if (s.valid) {
        ESP_LOGI(TAG, "temperatura %d,%d C, umidade %u %%", s.temperature_dc / 10,
                 (s.temperature_dc < 0 ? -s.temperature_dc : s.temperature_dc) % 10,
                 static_cast<unsigned>(s.humidity_pct));
    }
    if (on_report_) on_report_(s);
}

} // namespace cafey::app
