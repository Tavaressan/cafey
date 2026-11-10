#pragma once

// Mock da camada LEDC (PWM por hardware) do ESP-IDF para testes de host.
// Guarda o duty de cada canal para que os testes verifiquem o brilho do LED.

#include <cstdint>
#include <unordered_map>

#include "mock_esp_gpio.hpp"

typedef enum {
    LEDC_LOW_SPEED_MODE = 0,
    LEDC_HIGH_SPEED_MODE = 1,
} ledc_mode_t;

typedef enum {
    LEDC_TIMER_0 = 0,
    LEDC_TIMER_1 = 1,
    LEDC_TIMER_2 = 2,
    LEDC_TIMER_3 = 3,
} ledc_timer_t;

typedef enum {
    LEDC_CHANNEL_0 = 0,
    LEDC_CHANNEL_1 = 1,
    LEDC_CHANNEL_2 = 2,
    LEDC_CHANNEL_3 = 3,
    LEDC_CHANNEL_4 = 4,
    LEDC_CHANNEL_5 = 5,
    LEDC_CHANNEL_6 = 6,
    LEDC_CHANNEL_7 = 7,
} ledc_channel_t;

typedef enum {
    LEDC_TIMER_8_BIT = 8,
} ledc_timer_bit_t;

typedef enum {
    LEDC_AUTO_CLK = 0,
} ledc_clk_cfg_t;

typedef enum {
    LEDC_INTR_DISABLE = 0,
} ledc_intr_type_t;

typedef struct {
    ledc_mode_t speed_mode;
    ledc_timer_bit_t duty_resolution;
    ledc_timer_t timer_num;
    uint32_t freq_hz;
    ledc_clk_cfg_t clk_cfg;
    bool deconfigure;
} ledc_timer_config_t;

typedef struct {
    int gpio_num;
    ledc_mode_t speed_mode;
    ledc_channel_t channel;
    ledc_intr_type_t intr_type;
    ledc_timer_t timer_sel;
    uint32_t duty;
    int hpoint;
    struct {
        unsigned int output_invert : 1;
    } flags;
} ledc_channel_config_t;

class MockLedc {
public:
    static void reset() {
        duty_by_channel.clear();
        pin_by_channel.clear();
        timer_freq_hz = 0;
    }

    static uint32_t get_duty(ledc_channel_t channel) {
        auto it = duty_by_channel.find(channel);
        return it == duty_by_channel.end() ? 0 : it->second;
    }

    static int get_pin(ledc_channel_t channel) {
        auto it = pin_by_channel.find(channel);
        return it == pin_by_channel.end() ? -1 : it->second;
    }

    static std::unordered_map<int, uint32_t> duty_by_channel;
    static std::unordered_map<int, int> pin_by_channel;
    static uint32_t timer_freq_hz;
};

inline std::unordered_map<int, uint32_t> MockLedc::duty_by_channel;
inline std::unordered_map<int, int> MockLedc::pin_by_channel;
inline uint32_t MockLedc::timer_freq_hz = 0;

inline esp_err_t ledc_timer_config(const ledc_timer_config_t *cfg) {
    if (!cfg) return ESP_ERR_INVALID_ARG;
    MockLedc::timer_freq_hz = cfg->freq_hz;
    return ESP_OK;
}

inline esp_err_t ledc_channel_config(const ledc_channel_config_t *cfg) {
    if (!cfg) return ESP_ERR_INVALID_ARG;
    MockLedc::pin_by_channel[cfg->channel] = cfg->gpio_num;
    MockLedc::duty_by_channel[cfg->channel] = cfg->duty;
    return ESP_OK;
}

inline esp_err_t ledc_set_duty(ledc_mode_t, ledc_channel_t channel, uint32_t duty) {
    MockLedc::duty_by_channel[channel] = duty;
    return ESP_OK;
}

inline esp_err_t ledc_update_duty(ledc_mode_t, ledc_channel_t) {
    return ESP_OK;
}
