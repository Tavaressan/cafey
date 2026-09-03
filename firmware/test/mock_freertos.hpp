#pragma once

// Mock nativo do FreeRTOS para os testes de host (sem ESP-IDF).
// Fila: copia de bytes com semantica FIFO. Task: nao executa em thread real;
// o teste aciona o AO de forma sincrona via ActiveObject::process_pending().

#include <cstdint>
#include <cstring>
#include <deque>
#include <vector>

typedef int BaseType_t;
typedef unsigned int UBaseType_t;
typedef uint32_t TickType_t;

#define pdTRUE 1
#define pdFALSE 0
#define pdPASS 1
#define pdFAIL 0
#define portMAX_DELAY ((TickType_t)0xffffffffUL)
#define configMINIMAL_STACK_SIZE 768
#define tskIDLE_PRIORITY 0

#ifndef pdMS_TO_TICKS
#define pdMS_TO_TICKS(ms) ((TickType_t)(ms))
#endif

struct MockQueue {
    std::size_t item_size;
    std::size_t max_items;
    std::deque<std::vector<char>> items;
};

typedef MockQueue* QueueHandle_t;
typedef void* TaskHandle_t;
typedef void (*TaskFunction_t)(void*);

inline QueueHandle_t xQueueCreate(UBaseType_t length, UBaseType_t item_size) {
    return new MockQueue{static_cast<std::size_t>(item_size),
                         static_cast<std::size_t>(length),
                         {}};
}

inline void vQueueDelete(QueueHandle_t q) { delete q; }

inline BaseType_t xQueueSend(QueueHandle_t q, const void* item, TickType_t) {
    if (!q || q->items.size() >= q->max_items) return pdFALSE;
    std::vector<char> buf(q->item_size);
    std::memcpy(buf.data(), item, q->item_size);
    q->items.push_back(std::move(buf));
    return pdTRUE;
}

#define xQueueSendToBack xQueueSend

inline BaseType_t xQueueReceive(QueueHandle_t q, void* out, TickType_t) {
    if (!q || q->items.empty()) return pdFALSE;
    std::memcpy(out, q->items.front().data(), q->item_size);
    q->items.pop_front();
    return pdTRUE;
}

inline UBaseType_t uxQueueMessagesWaiting(QueueHandle_t q) {
    return q ? static_cast<UBaseType_t>(q->items.size()) : 0;
}

inline BaseType_t xTaskCreate(TaskFunction_t, const char*, uint32_t, void*,
                              UBaseType_t, TaskHandle_t* created) {
    if (created) *created = nullptr;
    return pdPASS;
}

inline void vTaskDelete(TaskHandle_t) {}
inline void vTaskDelay(TickType_t) {}
