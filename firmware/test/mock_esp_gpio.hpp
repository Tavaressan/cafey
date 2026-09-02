#pragma once

#include <cstdint>
#include <vector>
#include <string>
#include <unordered_map>
#include <iostream>

#define ESP_OK                    0
#define ESP_FAIL                 -1
#define ESP_ERR_INVALID_ARG       0x102
#define ESP_ERR_INVALID_STATE     0x103

typedef int esp_err_t;

typedef enum {
    GPIO_NUM_NC = -1,
    GPIO_NUM_0 = 0,
    GPIO_NUM_1 = 1,
    GPIO_NUM_2 = 2,
    GPIO_NUM_3 = 3,
    GPIO_NUM_4 = 4,
    GPIO_NUM_5 = 5,
    GPIO_NUM_6 = 6,
    GPIO_NUM_7 = 7,
    GPIO_NUM_8 = 8,
    GPIO_NUM_9 = 9,
    GPIO_NUM_10 = 10,
    GPIO_NUM_11 = 11,
    GPIO_NUM_12 = 12,
    GPIO_NUM_13 = 13,
    GPIO_NUM_14 = 14,
    GPIO_NUM_15 = 15,
    GPIO_NUM_16 = 16,
    GPIO_NUM_17 = 17,
    GPIO_NUM_18 = 18,
    GPIO_NUM_19 = 19,
    GPIO_NUM_20 = 20,
    GPIO_NUM_21 = 21,
    GPIO_NUM_22 = 22,
    GPIO_NUM_23 = 23,
    GPIO_NUM_25 = 25,
    GPIO_NUM_26 = 26,
    GPIO_NUM_27 = 27,
    GPIO_NUM_28 = 28,
    GPIO_NUM_29 = 29,
    GPIO_NUM_30 = 30,
    GPIO_NUM_31 = 31,
    GPIO_NUM_32 = 32,
    GPIO_NUM_33 = 33,
    GPIO_NUM_34 = 34,
    GPIO_NUM_35 = 35,
    GPIO_NUM_36 = 36,
    GPIO_NUM_37 = 37,
    GPIO_NUM_38 = 38,
    GPIO_NUM_39 = 39,
    GPIO_NUM_MAX = 40
} gpio_num_t;

typedef enum {
    GPIO_MODE_DISABLE = 0,
    GPIO_MODE_INPUT = 1,
    GPIO_MODE_OUTPUT = 2,
    GPIO_MODE_OUTPUT_OD = 3,
    GPIO_MODE_INPUT_OUTPUT_OD = 4,
    GPIO_MODE_INPUT_OUTPUT = 5,
} gpio_mode_t;

typedef enum {
    GPIO_PULLUP_DISABLE = 0x0,
    GPIO_PULLUP_ENABLE = 0x1,
} gpio_pullup_t;

typedef enum {
    GPIO_PULLDOWN_DISABLE = 0x0,
    GPIO_PULLDOWN_ENABLE = 0x1,
} gpio_pulldown_t;

typedef enum {
    GPIO_INTR_DISABLE = 0,
    GPIO_INTR_POSEDGE = 1,
    GPIO_INTR_NEGEDGE = 2,
    GPIO_INTR_ANYEDGE = 3,
    GPIO_INTR_LOW_LEVEL = 4,
    GPIO_INTR_HIGH_LEVEL = 5,
    GPIO_INTR_MAX
} gpio_int_type_t;

typedef struct {
    uint64_t pin_bit_mask;
    gpio_mode_t mode;
    gpio_pullup_t pull_up_en;
    gpio_pulldown_t pull_down_en;
    gpio_int_type_t intr_type;
} gpio_config_t;

#define ESP_LOGI(tag, fmt, ...) do {} while(0)
#define ESP_LOGW(tag, fmt, ...) do {} while(0)
#define ESP_LOGE(tag, fmt, ...) do {} while(0)
#define ESP_LOGD(tag, fmt, ...) do {} while(0)

class MockGpio {
public:
    struct PinState {
        int level = 0;
        gpio_mode_t mode = GPIO_MODE_DISABLE;
        gpio_pullup_t pull_up = GPIO_PULLUP_DISABLE;
        gpio_pulldown_t pull_down = GPIO_PULLDOWN_DISABLE;
        gpio_int_type_t intr_type = GPIO_INTR_DISABLE;
        bool is_configured = false;
        int set_level_call_count = 0;
    };

    static void reset() {
        pins.clear();
        history.clear();
        config_ret_code = ESP_OK;
        set_level_ret_code = ESP_OK;
    }

    static PinState& get_pin(gpio_num_t pin) {
        return pins[pin];
    }

    static const std::vector<std::string>& get_history() {
        return history;
    }

    static void record_event(const std::string& evt) {
        history.push_back(evt);
    }

    static esp_err_t config_ret_code;
    static esp_err_t set_level_ret_code;

private:
    static std::unordered_map<int, PinState> pins;
    static std::vector<std::string> history;
};

inline std::unordered_map<int, MockGpio::PinState> MockGpio::pins;
inline std::vector<std::string> MockGpio::history;
inline esp_err_t MockGpio::config_ret_code = ESP_OK;
inline esp_err_t MockGpio::set_level_ret_code = ESP_OK;

inline esp_err_t gpio_config(const gpio_config_t *cfg) {
    if (!cfg) return ESP_ERR_INVALID_ARG;
    if (MockGpio::config_ret_code != ESP_OK) return MockGpio::config_ret_code;

    for (int i = 0; i < GPIO_NUM_MAX; ++i) {
        if (cfg->pin_bit_mask & (1ULL << i)) {
            // GPIOs 34-39 are input only on ESP32
            if (i >= 34 && i <= 39 && (cfg->mode == GPIO_MODE_OUTPUT || cfg->mode == GPIO_MODE_INPUT_OUTPUT)) {
                return ESP_ERR_INVALID_ARG;
            }
            auto& pin = MockGpio::get_pin((gpio_num_t)i);
            pin.mode = cfg->mode;
            pin.pull_up = cfg->pull_up_en;
            pin.pull_down = cfg->pull_down_en;
            pin.intr_type = cfg->intr_type;
            pin.is_configured = true;
            MockGpio::record_event("gpio_config:" + std::to_string(i));
        }
    }
    return ESP_OK;
}

inline esp_err_t gpio_set_level(gpio_num_t gpio_num, uint32_t level) {
    if (gpio_num < 0 || gpio_num >= GPIO_NUM_MAX) return ESP_ERR_INVALID_ARG;
    if (gpio_num >= 34 && gpio_num <= 39) return ESP_ERR_INVALID_ARG;
    if (MockGpio::set_level_ret_code != ESP_OK) return MockGpio::set_level_ret_code;

    auto& pin = MockGpio::get_pin(gpio_num);
    pin.level = level ? 1 : 0;
    pin.set_level_call_count++;
    MockGpio::record_event("gpio_set_level:" + std::to_string(gpio_num) + "=" + std::to_string(pin.level));
    return ESP_OK;
}

inline int gpio_get_level(gpio_num_t gpio_num) {
    if (gpio_num < 0 || gpio_num >= GPIO_NUM_MAX) return 0;
    return MockGpio::get_pin(gpio_num).level;
}

inline esp_err_t gpio_reset_pin(gpio_num_t gpio_num) {
    if (gpio_num < 0 || gpio_num >= GPIO_NUM_MAX) return ESP_ERR_INVALID_ARG;
    auto& pin = MockGpio::get_pin(gpio_num);
    pin = MockGpio::PinState();
    MockGpio::record_event("gpio_reset_pin:" + std::to_string(gpio_num));
    return ESP_OK;
}
