#pragma once

#if defined(ESP_PLATFORM)
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#else
#include <mutex>
#endif

namespace cafey::core {

/**
 * @brief Portable mutex abstraction.
 *
 * On ESP-IDF (ESP_PLATFORM defined), backed by a FreeRTOS recursive mutex
 * semaphore so nested locking from the same task is safe. On host builds
 * (unit tests), backed by std::mutex.
 *
 * This is the single point of coordination for concurrent access to NVS
 * (Non-Volatile Storage) described in spec §5.4.
 */
class Mutex {
public:
    Mutex();
    ~Mutex();

    Mutex(const Mutex&) = delete;
    Mutex& operator=(const Mutex&) = delete;

    void lock();
    void unlock();

private:
#if defined(ESP_PLATFORM)
    SemaphoreHandle_t handle_;
#else
    std::recursive_mutex handle_;
#endif
};

/**
 * @brief RAII lock guard for cafey::core::Mutex.
 */
class LockGuard {
public:
    explicit LockGuard(Mutex& mutex) : mutex_(mutex) { mutex_.lock(); }
    ~LockGuard() { mutex_.unlock(); }

    LockGuard(const LockGuard&) = delete;
    LockGuard& operator=(const LockGuard&) = delete;

private:
    Mutex& mutex_;
};

} // namespace cafey::core
