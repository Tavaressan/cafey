cmake_minimum_required(VERSION 3.16)
project(cafey_firmware_tests CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/../main/drivers
    ${CMAKE_CURRENT_SOURCE_DIR}/../main/core
    ${CMAKE_CURRENT_SOURCE_DIR}/../main
)

add_executable(test_relay
    test_relay.cpp
    ../main/drivers/relay.cpp
)

add_executable(test_reconnect_policy
    test_reconnect_policy.cpp
    ../main/core/reconnect_policy.cpp
)

add_executable(test_wifi_credentials
    test_wifi_credentials.cpp
    ../main/core/wifi_credentials.cpp
)

enable_testing()
add_test(NAME RelayDriverTest COMMAND test_relay)
add_test(NAME ReconnectPolicyTest COMMAND test_reconnect_policy)
add_test(NAME WifiCredentialsStoreTest COMMAND test_wifi_credentials)
