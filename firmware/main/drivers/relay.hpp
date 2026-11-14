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
 * - Trigger polarity is configurable (HW-27, issue #305):
 *   - ActiveLevel::High (default): Fotek SSR-40DA. HIGH = load ON, LOW = load OFF.
 *     External R1 10k **pull-down** keeps the SSR input LOW while the GPIO is
 *     high-impedance (power-on, reset, flashing).
 *   - ActiveLevel::Low: legacy HL-52S relay module. LOW = load ON, HIGH = load OFF.
 *     External R1 10k **pull-up** keeps the input HIGH while the GPIO floats.
 *   The external resistor must match the polarity: an active-high firmware on an
 *   active-low module (or vice versa) switches the load ON at init().
 * - To guarantee zero spurious pulses at power-on / boot, the driver explicitly forces
 *   the GPIO to the idle (OFF) level before and upon configuring the output direction.
 * - RAII: Safely turns off the relay on object destruction.
 */
class Relay {
public:
    /// Logic level on the signal pin that switches the load ON.
    enum class ActiveLevel : uint8_t { High, Low };

    /**
     * @brief Construct a new Relay instance.
     * @param pin GPIO pin number (defaults to GPIO_NUM_26).
     * @param auto_init If true, automatically calls init() in the constructor.
     * @param active_level Level that turns the load ON (defaults to High, SSR-40DA).
     */
    explicit Relay(gpio_num_t pin = GPIO_NUM_26, bool auto_init = false,
                   ActiveLevel active_level = ActiveLevel::High);

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
     * Forces the idle (OFF) level before output configuration to prevent any boot pulse glitch.
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

    /**
     * @brief Get configured trigger polarity.
     */
    [[nodiscard]] ActiveLevel active_level() const noexcept;

private:
    [[nodiscard]] uint32_t level_for(bool on) const noexcept;

    gpio_num_t pin_;
    ActiveLevel active_level_;
    bool is_on_;
    bool is_initialized_;
};

} // namespace cafey::drivers
