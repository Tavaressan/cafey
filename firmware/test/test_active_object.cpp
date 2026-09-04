#include <iostream>
#include <vector>

#include "core/active_object.hpp"

#define TEST_ASSERT(cond, msg)                                                  \
    do {                                                                       \
        if (!(cond)) {                                                         \
            std::cerr << "FAILED: " << msg << " (" << __FILE__ << ":"          \
                      << __LINE__ << ")" << std::endl;                         \
            return 1;                                                          \
        }                                                                      \
    } while (0)

using cafey::core::ActiveObject;
using cafey::core::Message;
using cafey::core::MessageType;

// AO de teste: registra toda mensagem recebida no dispatch.
class CountingAO : public ActiveObject {
public:
    CountingAO() : ActiveObject("counting", 4) {}

    std::vector<Message> received;
    int start_calls = 0;
    int tick_calls = 0;

protected:
    void dispatch(const Message& msg) override { received.push_back(msg); }
    void on_start() override { ++start_calls; }
    void on_tick() override { ++tick_calls; }
};

int test_queue_lifecycle() {
    CountingAO ao;
    TEST_ASSERT(ao.init(), "init() deve criar a fila");
    TEST_ASSERT(ao.init(), "init() deve ser idempotente");
    TEST_ASSERT(ao.start(), "start() deve ter sucesso");
    TEST_ASSERT(ao.is_running(), "AO deve estar rodando apos start()");
    std::cout << "[PASS] test_queue_lifecycle" << std::endl;
    return 0;
}

int test_post_and_dispatch_fifo() {
    CountingAO ao;
    ao.start();

    TEST_ASSERT(ao.post(Message{MessageType::ButtonPressed, 10}), "post 1");
    TEST_ASSERT(ao.post(Message{MessageType::StartBrew, 20}), "post 2");
    TEST_ASSERT(ao.post(Message{MessageType::StopBrew, 30}), "post 3");
    TEST_ASSERT(ao.pending_count() == 3, "3 mensagens pendentes antes do dispatch");
    TEST_ASSERT(ao.received.empty(), "nada despachado antes de process_pending()");

    TEST_ASSERT(ao.process_pending(), "process_pending drena a fila");
    TEST_ASSERT(ao.pending_count() == 0, "fila vazia apos drenar");
    TEST_ASSERT(ao.received.size() == 3, "3 mensagens despachadas");

    // Ordem FIFO preservada, tipos distintos chegam ao dispatch.
    TEST_ASSERT(ao.received[0].type == MessageType::ButtonPressed && ao.received[0].arg == 10,
                "1a mensagem = ButtonPressed(10)");
    TEST_ASSERT(ao.received[1].type == MessageType::StartBrew && ao.received[1].arg == 20,
                "2a mensagem = StartBrew(20)");
    TEST_ASSERT(ao.received[2].type == MessageType::StopBrew && ao.received[2].arg == 30,
                "3a mensagem = StopBrew(30)");

    std::cout << "[PASS] test_post_and_dispatch_fifo" << std::endl;
    return 0;
}

int test_queue_full_rejects() {
    CountingAO ao; // fila de tamanho 4
    ao.start();
    for (int i = 0; i < 4; ++i) {
        TEST_ASSERT(ao.post(Message{MessageType::CommandReceived, i}), "post dentro da capacidade");
    }
    TEST_ASSERT(!ao.post(Message{MessageType::CommandReceived, 99}), "post deve falhar com fila cheia");
    TEST_ASSERT(ao.pending_count() == 4, "capacidade respeitada");
    std::cout << "[PASS] test_queue_full_rejects" << std::endl;
    return 0;
}

int test_post_before_init_fails() {
    CountingAO ao;
    TEST_ASSERT(!ao.post(Message{MessageType::ButtonPressed, 0}),
                "post antes de init()/start() deve falhar");
    TEST_ASSERT(!ao.process_pending(), "process_pending sem fila retorna false");
    std::cout << "[PASS] test_post_before_init_fails" << std::endl;
    return 0;
}

int main() {
    std::cout << "Running Cafey ActiveObject Unit Tests..." << std::endl;
    if (test_queue_lifecycle()) return 1;
    if (test_post_and_dispatch_fifo()) return 1;
    if (test_queue_full_rejects()) return 1;
    if (test_post_before_init_fails()) return 1;
    std::cout << "All ActiveObject tests PASSED!" << std::endl;
    return 0;
}
