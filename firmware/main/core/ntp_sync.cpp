#include "core/ntp_sync.hpp"

#include "esp_sntp.h"

namespace cafey::core {

std::function<void()> NtpSync::s_on_synced;

NtpSync::NtpSync(std::function<void()> on_synced) {
    s_on_synced = std::move(on_synced);
}

void NtpSync::start() {
    esp_sntp_setoperatingmode(ESP_SNTP_OPMODE_POLL);
    esp_sntp_setservername(0, "pool.ntp.org");
    sntp_set_time_sync_notification_cb(&NtpSync::on_sync_notification);
    esp_sntp_init();
}

void NtpSync::on_sync_notification(struct timeval* /*tv*/) {
    if (s_on_synced) s_on_synced();
}

} // namespace cafey::core
