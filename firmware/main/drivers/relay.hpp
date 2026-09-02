#pragma once

#include <cstdint>
#include <stdbool.h>

#if defined(ESP_PLATFORM)
#include "driver/gpio.h"
#include "esp_err.h"
#include "esp_log.h"
#else
#include "mock_esp_gpio.hpp"
#endif

namespace cafey::drivers {

/**
 * @brief Relay driver for Cafey IoT coffee maker.
 *
 * Hardware design specification:
 * - Signal pin: GPIO 26 (default)
 * - Trigger: Active HIGH (HIGH = Relay energised / Coffee maker ON, LOW = Relay open / Coffee maker OFF)
 * - External R1 10k pull-down: keeps GPIO at 0V during high-impedance (boot).
 *   To guarantee zero spurious pulses at power-on / boot, the driver explicitly forces
 *   the GPIO level to LOW before and upon configuring the output direction.
 * - RAII: Safely turns off the relay on object destruction.
 */
class Relay {
public:
    /**
     * @brief Construct a new Relay instance.
     * @param pin GPIO pin number (defaults to GPIO_NUM_26).
     * @param auto_init If true, automatically calls init() in the constructor.
     */
    explicit Relay(gpio_num_t pin = GPIO_NUM_26, bool auto_init = false);

    /**
     * @brief Destructor. Ensures relay is safely deactivated on destruction.
     */
    ~Relay();

    // Disable copy semantics to prevent duplicate hardware ownership
    Relay(const Relay&) = delete;
    Relay& operator=(const Relay&) = delete;

    // Enable move semantics
    Relay(Relay&& other) noexcept;
    Relay& operator=(Relay&& other) noexcept;

    /**
     * @brief Initialize the relay GPIO.
     * Forces LOW before output configuration to prevent any boot pulse glitch.
     * @return ESP_OK on success, or ESP-IDF error code.
     */
    esp_err_t init();

    /**
     * @brief Set relay state.
     * @param on true to energize relay (turn ON), false to de-energize (turn OFF).
     * @return ESP_OK on success, ESP_ERR_INVALID_STATE if not initialized, or error code.
     */
    esp_err_t set(bool on);

    /**
     * @brief Turn relay ON (closes contact, turns coffee maker on).
     * @return ESP_OK on success, or error code.
     */
    esp_err_t turn_on();

    /**
     * @brief Turn relay OFF (opens contact, turns coffee maker off).
     * @return ESP_OK on success, or error code.
     */
    esp_err_t turn_off();

    /**
     * @brief Toggle relay state.
     * @return ESP_OK on success, or error code.
     */
    esp_err_t toggle();

    /**
     * @brief Check if relay is currently ON.
     * @return true if ON (energized), false if OFF.
     */
    [[nodiscard]] bool is_on() const noexcept;

    /**
     * @brief Check if relay driver has been initialized.
     * @return true if init() succeeded, false otherwise.
     */
    [[nodiscard]] bool is_initialized() const noexcept;

    /**
     * @brief Get configured GPIO pin.
     * @return gpio_num_t pin number.
     */
    [[nodiscard]] gpio_num_t get_pin() const noexcept;

private:
    gpio_num_t pin_;
    bool is_on_;
    bool is_initialized_;
};

} // namespace cafey::drivers
