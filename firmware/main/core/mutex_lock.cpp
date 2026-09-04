#include "mutex_lock.hpp"

namespace cafey::core {

#if defined(ESP_PLATFORM)

Mutex::Mutex() : handle_(xSemaphoreCreateRecursiveMutex()) {}

Mutex::~Mutex() {
    if (handle_ != nullptr) {
        vSemaphoreDelete(handle_);
    }
}

void Mutex::lock() {
    xSemaphoreTakeRecursive(handle_, portMAX_DELAY);
}

void Mutex::unlock() {
    xSemaphoreGiveRecursive(handle_);
}

#else

Mutex::Mutex() = default;
Mutex::~Mutex() = default;

void Mutex::lock() {
    handle_.lock();
}

void Mutex::unlock() {
    handle_.unlock();
}

#endif

} // namespace cafey::core
