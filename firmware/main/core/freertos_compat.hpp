#pragma once

// Abstrai o FreeRTOS: no alvo usa o real; no host de testes usa o mock nativo.
#if defined(ESP_PLATFORM)
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "freertos/queue.h"
#else
#include "mock_freertos.hpp"
#endif
