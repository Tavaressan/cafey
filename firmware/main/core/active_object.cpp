#include "active_object.hpp"

namespace cafey::core {

ActiveObject::ActiveObject(const char* name, uint32_t queue_length,
                           uint32_t stack_size, uint32_t priority,
                           uint32_t tick_period_ms)
    : name_(name),
      queue_length_(queue_length),
      stack_size_(stack_size),
      priority_(priority),
      tick_ticks_(tick_period_ms ? pdMS_TO_TICKS(tick_period_ms) : portMAX_DELAY) {}

ActiveObject::~ActiveObject() {
    stop();
    if (task_) {
        vTaskDelete(task_);
        task_ = nullptr;
    }
    if (queue_) {
        vQueueDelete(queue_);
        queue_ = nullptr;
    }
}

bool ActiveObject::init() {
    if (queue_) return true;
    queue_ = xQueueCreate(queue_length_, sizeof(Message));
    return queue_ != nullptr;
}

bool ActiveObject::start() {
    if (!init()) return false;
    if (running_) return true;
    running_ = true;
    if (xTaskCreate(&ActiveObject::task_entry, name_, stack_size_, this,
                    priority_, &task_) != pdPASS) {
        running_ = false;
        return false;
    }
    return true;
}

void ActiveObject::stop() {
    if (!running_) return;
    running_ = false;
    if (queue_) {
        Message wake{};
        xQueueSend(queue_, &wake, 0); // desbloqueia o receive do laco
    }
}

bool ActiveObject::post(const Message& msg) {
    if (!queue_) return false;
    return xQueueSend(queue_, &msg, 0) == pdTRUE;
}

bool ActiveObject::process_pending() {
    if (!queue_) return false;
    Message msg;
    bool any = false;
    while (xQueueReceive(queue_, &msg, 0) == pdTRUE) {
        dispatch(msg);
        any = true;
    }
    return any;
}

std::size_t ActiveObject::pending_count() const {
    return queue_ ? static_cast<std::size_t>(uxQueueMessagesWaiting(queue_)) : 0;
}

void ActiveObject::task_entry(void* self) {
    static_cast<ActiveObject*>(self)->run();
}

void ActiveObject::run() {
    on_start();
    Message msg;
    while (running_) {
        if (xQueueReceive(queue_, &msg, tick_ticks_) == pdTRUE) {
            dispatch(msg);
        } else {
            on_tick();
        }
    }
    vTaskDelete(nullptr);
}

} // namespace cafey::core
