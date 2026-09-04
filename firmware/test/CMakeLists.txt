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

enable_testing()

add_executable(test_relay
    test_relay.cpp
    ../main/drivers/relay.cpp
)
add_test(NAME RelayDriverTest COMMAND test_relay)

add_executable(test_active_object
    test_active_object.cpp
    ../main/core/active_object.cpp
)
add_test(NAME ActiveObjectTest COMMAND test_active_object)

add_executable(test_cafeteira
    test_cafeteira.cpp
    ../main/core/active_object.cpp
    ../main/app/cafeteira.cpp
    ../main/app/conectividade.cpp
    ../main/app/agendador.cpp
    ../main/drivers/relay.cpp
)
add_test(NAME CafeteiraActiveObjectTest COMMAND test_cafeteira)

add_executable(test_schedule_store
    test_schedule_store.cpp
    ../main/storage/schedule_store.cpp
    ../main/storage/nvs_store.cpp
    ../main/core/mutex_lock.cpp
)
add_test(NAME ScheduleStoreTest COMMAND test_schedule_store)

add_executable(test_event_queue_store
    test_event_queue_store.cpp
    ../main/storage/event_queue_store.cpp
    ../main/storage/nvs_store.cpp
    ../main/core/mutex_lock.cpp
)
add_test(NAME EventQueueStoreTest COMMAND test_event_queue_store)

add_executable(test_reconnect_policy
    test_reconnect_policy.cpp
    ../main/core/reconnect_policy.cpp
)
add_test(NAME ReconnectPolicyTest COMMAND test_reconnect_policy)

add_executable(test_wifi_credentials
    test_wifi_credentials.cpp
    ../main/core/wifi_credentials.cpp
)
add_test(NAME WifiCredentialsStoreTest COMMAND test_wifi_credentials)
