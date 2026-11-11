#pragma once

// Encapsula Task Watchdog e motivo de reset do ESP-IDF para permitir mock
// em testes de host (FW-31).

#if defined(ESP_PLATFORM)
#include "esp_task_wdt.h"
#include "esp_system.h"
#else
#include "mock_esp_task_wdt.hpp"
#endif

namespace cafey::core {

inline esp_err_t task_wdt_subscribe_current() {
    return esp_task_wdt_add(nullptr);
}

inline esp_err_t task_wdt_feed() {
    return esp_task_wdt_reset();
}

inline esp_err_t task_wdt_unsubscribe_current() {
    return esp_task_wdt_delete(nullptr);
}

inline esp_reset_reason_t system_reset_reason() {
    return esp_reset_reason();
}

} // namespace cafey::core
