cmake_minimum_required(VERSION 3.16)
project(cafey_firmware_tests CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/../main/drivers
    ${CMAKE_CURRENT_SOURCE_DIR}/../main/core
    ${CMAKE_CURRENT_SOURCE_DIR}/../main/storage
    ${CMAKE_CURRENT_SOURCE_DIR}/../main
)

add_executable(test_relay
    test_relay.cpp
    ../main/drivers/relay.cpp
)

add_executable(test_schedule_store
    test_schedule_store.cpp
    ../main/storage/schedule_store.cpp
    ../main/storage/nvs_store.cpp
    ../main/core/mutex_lock.cpp
)

add_executable(test_event_queue_store
    test_event_queue_store.cpp
    ../main/storage/event_queue_store.cpp
    ../main/storage/nvs_store.cpp
    ../main/core/mutex_lock.cpp
)

enable_testing()
add_test(NAME RelayDriverTest COMMAND test_relay)
add_test(NAME ScheduleStoreTest COMMAND test_schedule_store)
add_test(NAME EventQueueStoreTest COMMAND test_event_queue_store)
