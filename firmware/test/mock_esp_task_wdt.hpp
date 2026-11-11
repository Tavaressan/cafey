#pragma once

// Mock host-side das APIs de Task Watchdog e motivo de reset do ESP-IDF,
// para testes de unidade sem hardware.

#include <cstdint>

#ifndef ESP_OK
#define ESP_OK 0
#endif
#ifndef ESP_FAIL
#define ESP_FAIL -1
#endif
#ifndef ESP_ERR_INVALID_STATE
#define ESP_ERR_INVALID_STATE 0x103
#endif

typedef int esp_err_t;

typedef enum {
    ESP_RST_UNKNOWN = 0,
    ESP_RST_POWERON = 1,
    ESP_RST_EXT = 2,
    ESP_RST_SW = 3,
    ESP_RST_PANIC = 4,
    ESP_RST_INT_WDT = 5,
    ESP_RST_TASK_WDT = 6,
    ESP_RST_WDT = 7,
    ESP_RST_DEEPSLEEP = 8,
    ESP_RST_BROWNOUT = 9,
    ESP_RST_SDIO = 10,
    ESP_RST_USB = 11,
    ESP_RST_JTAG = 12,
    ESP_RST_EFUSE = 13,
    ESP_RST_PWR_GLITCH = 14,
    ESP_RST_CPU_LOCKUP = 15,
} esp_reset_reason_t;

class MockTaskWdt {
public:
    static void reset() {
        add_calls = 0;
        reset_calls = 0;
        delete_calls = 0;
        add_should_fail = false;
        last_reset_reason = ESP_RST_POWERON;
    }

    static int add_calls;
    static int reset_calls;
    static int delete_calls;
    static bool add_should_fail;
    static esp_reset_reason_t last_reset_reason;
};

inline int MockTaskWdt::add_calls = 0;
inline int MockTaskWdt::reset_calls = 0;
inline int MockTaskWdt::delete_calls = 0;
inline bool MockTaskWdt::add_should_fail = false;
inline esp_reset_reason_t MockTaskWdt::last_reset_reason = ESP_RST_POWERON;

// Assinaturas compativeis com esp_task_wdt.h (uso simplificado).
inline esp_err_t esp_task_wdt_add(void* /*handle*/) {
    ++MockTaskWdt::add_calls;
    return MockTaskWdt::add_should_fail ? ESP_FAIL : ESP_OK;
}

inline esp_err_t esp_task_wdt_reset() {
    ++MockTaskWdt::reset_calls;
    return ESP_OK;
}

inline esp_err_t esp_task_wdt_delete(void* /*handle*/) {
    ++MockTaskWdt::delete_calls;
    return ESP_OK;
}

inline esp_reset_reason_t esp_reset_reason() {
    return MockTaskWdt::last_reset_reason;
}
