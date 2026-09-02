#include "relay.hpp"

namespace cafey::drivers {

Relay::Relay(gpio_num_t pin, bool auto_init)
    : pin_(pin),
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

    // Step 1: Force GPIO level LOW BEFORE configuring output mode to guarantee
    // zero glitch when switching from high-impedance to output.
    // External 10k R1 pull-down holds 0V; setting low level first maintains this.
    gpio_set_level(pin_, 0);

    // Step 2: Configure GPIO as output, pull-up/down disabled (external R1 handles pull-down),
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

    // Step 3: Re-assert LOW state immediately after configuration
    err = gpio_set_level(pin_, 0);
    if (err != ESP_OK) {
        ESP_LOGE("Relay", "Failed to set GPIO %d level: %d", pin_, err);
        return err;
    }

    is_on_ = false;
    is_initialized_ = true;
    ESP_LOGI("Relay", "Relay initialized on GPIO %d (Active HIGH, default OFF)", pin_);
    return ESP_OK;
}

esp_err_t Relay::set(bool on) {
    if (!is_initialized_) {
        ESP_LOGE("Relay", "Relay not initialized on GPIO %d", pin_);
        return ESP_ERR_INVALID_STATE;
    }

    esp_err_t err = gpio_set_level(pin_, on ? 1 : 0);
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

} // namespace cafey::drivers
