#include "relay.hpp"

namespace cafey::drivers {

Relay::Relay(gpio_num_t pin, bool auto_init, ActiveLevel active_level)
    : pin_(pin),
      active_level_(active_level),
      is_on_(false),
      is_initialized_(false) {
    if (auto_init) {
        init();
    }
}

Relay::~Relay() {
    if (is_initialized_) {
        // Safe de-energize on destruction
        turn_off();
    }
}

Relay::Relay(Relay&& other) noexcept
    : pin_(other.pin_),
      active_level_(other.active_level_),
      is_on_(other.is_on_),
      is_initialized_(other.is_initialized_) {
    other.is_on_ = false;
    other.is_initialized_ = false;
    other.pin_ = GPIO_NUM_NC;
}

Relay& Relay::operator=(Relay&& other) noexcept {
    if (this != &other) {
        if (is_initialized_) {
            turn_off();
        }
        pin_ = other.pin_;
        active_level_ = other.active_level_;
        is_on_ = other.is_on_;
        is_initialized_ = other.is_initialized_;

        other.is_on_ = false;
        other.is_initialized_ = false;
        other.pin_ = GPIO_NUM_NC;
    }
    return *this;
}

esp_err_t Relay::init() {
    // Validate pin range (valid GPIOs for ESP32 output are 0-33)
    if (pin_ < 0 || pin_ >= GPIO_NUM_MAX || (pin_ >= 34 && pin_ <= 39)) {
        ESP_LOGE("Relay", "Invalid GPIO pin %d for relay output", pin_);
        return ESP_ERR_INVALID_ARG;
    }

    // Set the idle (OFF) level before switching from high-impedance to output.
    // External R1 holds the line at the idle level while the ESP32 GPIO is
    // floating (pull-down for active-high, pull-up for active-low).
    const uint32_t idle_level = level_for(false);
    gpio_set_level(pin_, idle_level);

    // Configure GPIO as output with internal pull-up/down disabled (external R1 handles the bias),
    // interrupts disabled.
    const gpio_config_t io_conf = {
        .pin_bit_mask = (1ULL << pin_),
        .mode = GPIO_MODE_OUTPUT,
        .pull_up_en = GPIO_PULLUP_DISABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type = GPIO_INTR_DISABLE,
    };

    esp_err_t err = gpio_config(&io_conf);
    if (err != ESP_OK) {
        ESP_LOGE("Relay", "Failed to configure GPIO %d: %d", pin_, err);
        return err;
    }

    // Re-assert the idle level immediately after configuration.
    err = gpio_set_level(pin_, idle_level);

    if (err != ESP_OK) {
        ESP_LOGE("Relay", "Failed to set GPIO %d level: %d", pin_, err);
        return err;
    }

    is_on_ = false;
    is_initialized_ = true;
    ESP_LOGI("Relay", "Relay initialized on GPIO %d (Active %s, default OFF)", pin_,
             active_level_ == ActiveLevel::High ? "HIGH" : "LOW");
    return ESP_OK;
}

esp_err_t Relay::set(bool on) {
    if (!is_initialized_) {
        ESP_LOGE("Relay", "Relay not initialized on GPIO %d", pin_);
        return ESP_ERR_INVALID_STATE;
    }
    const uint32_t level = level_for(on);
    esp_err_t err = gpio_set_level(pin_, level);

    if (err == ESP_OK) {
        is_on_ = on;
        ESP_LOGI("Relay", "Relay on GPIO %d -> %s", pin_, on ? "ON" : "OFF");
    } else {
        ESP_LOGE("Relay", "Failed to set relay on GPIO %d: %d", pin_, err);
    }
    return err;
}

esp_err_t Relay::turn_on() {
    return set(true);
}

esp_err_t Relay::turn_off() {
    return set(false);
}

esp_err_t Relay::toggle() {
    return set(!is_on_);
}

bool Relay::is_on() const noexcept {
    return is_on_;
}

bool Relay::is_initialized() const noexcept {
    return is_initialized_;
}

gpio_num_t Relay::get_pin() const noexcept {
    return pin_;
}

Relay::ActiveLevel Relay::active_level() const noexcept {
    return active_level_;
}

uint32_t Relay::level_for(bool on) const noexcept {
    const bool high = (active_level_ == ActiveLevel::High) ? on : !on;
    return high ? 1U : 0U;
}

} // namespace cafey::drivers
