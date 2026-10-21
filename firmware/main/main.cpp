/*
 * Cafey - Firmware do modulo IoT (ESP-IDF C++17)
 *
 * Estrutura de Active Objects sobre FreeRTOS (spec 5.2 / FW-02):
 * cada componente e uma task com fila propria e maquina de estados interna.
 * Sem memoria compartilhada — comunicacao so por eventos assincronos.
 *
 * Pinagem (conforme STATUS.md e esquematico KiCad):
 * - Rele (GPIO26): gatilho de nivel BAIXO (HL-52S, LOW = liga), R1 pull-up 10k externo
 * - Botao (GPIO27): pull-up externo R2 10k + C3 100nF, solto=1, pressionado=0
 * - LED RGB: catodo comum (R=21, G=22, B=23)
 */

#include <string>

#include "esp_log.h"
#include "esp_random.h"
#include "core/esp_mqtt_client.hpp"
#include "core/mqtt_config_store.hpp"
#include "core/mqtt_topics.hpp"
#include "core/ntp_sync.hpp"
#include "core/wifi_manager.hpp"
#include "storage/schedule_store.hpp"
#include "storage/event_id_generator.hpp"
#include "storage/event_queue_store.hpp"

#include "app/agendador.hpp"
#include "app/ble_event_proxy.hpp"
#include "app/ble_service.hpp"
#include "app/cafeteira.hpp"
#include "app/coffee_command.hpp"
#include "app/conectividade.hpp"
#include "app/schedule_payload.hpp"
#include "app/timezone_offset.hpp"
#include "app/uplink_publisher.hpp"
#include "core/esp_nimble_server.hpp"

namespace {
const char* TAG = "cafey";
}

// AOs com duracao de armazenamento estatica: vivem por toda a execucao.
cafey::app::Cafeteira g_cafeteira;
cafey::app::Conectividade g_conectividade;
cafey::app::Agendador g_agendador;

extern "C" void app_main(void) {
    using cafey::core::Message;
    using cafey::core::MessageType;

    ESP_LOGI(TAG, "boot - Cafey IoT (Active Objects)");

    // Fila da Conectividade antes de qualquer produtor (Wi-Fi, MQTT, Cafeteira):
    // post() em fila inexistente descarta a mensagem.
    g_conectividade.init();

    // Carrega agendamentos e fila de eventos persistidos em NVS (spec §5.4):
    // sobrevivem a reboot e queda de energia. Consumidos futuramente pelos
    // Active Objects Agendador e Conectividade.
    static cafey::storage::ScheduleStore schedule_store;
    esp_err_t err = schedule_store.init();
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "Falha ao carregar agendamentos de NVS: %d", err);
    } else {
        ESP_LOGI(TAG, "agendamentos carregados de NVS: %d", static_cast<int>(schedule_store.count()));
    }

    static cafey::storage::EventQueueStore event_queue_store;
    err = event_queue_store.init();
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "Falha ao carregar fila de eventos de NVS: %d", err);
    } else {
        ESP_LOGI(TAG, "eventos pendentes carregados de NVS: %d", static_cast<int>(event_queue_store.size()));
    }

    static cafey::storage::EventIdGenerator event_id_gen;
    err = event_id_gen.init(esp_random());
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "Falha ao inicializar gerador de ID de evento: %d", err);
    }

    g_cafeteira.attach_event_queue(&event_queue_store, &event_id_gen);
    g_cafeteira.set_on_event_queued([] {
        g_conectividade.post(Message{MessageType::EventQueued, 0});
    });
    g_conectividade.attach_event_queue(&event_queue_store);

    // Inicializa Wi-Fi (STA) com reconexao automatica em backoff e credenciais
    // de provisionamento persistidas em NVS (UC-04 #249). Sem credenciais gravadas,
    // fica aguardando o app/BLE provisionar via WifiManager::provision().
    static cafey::app::BleService* g_ble_service = nullptr;
    static cafey::core::WifiManager wifi_manager;
    wifi_manager.set_on_connected([] {
        g_conectividade.post(Message{MessageType::WifiConnected, 0});
        if (g_ble_service != nullptr) {
            g_ble_service->publish_wifi_status("CONNECTED");
        }
    });
    wifi_manager.set_on_disconnected([] {
        g_conectividade.post(Message{MessageType::WifiDisconnected, 0});
        if (g_ble_service != nullptr) {
            g_ble_service->publish_wifi_status("DISCONNECTED");
        }
    });
    esp_err_t wifi_err = wifi_manager.init();
    if (wifi_err != ESP_OK) {
        ESP_LOGE(TAG, "Falha ao inicializar Wi-Fi: %d", wifi_err);
    }

    // Disparo do agendamento: o Agendador aciona o preparo pelo relogio interno
    // (spec §5.5), postando StartBrew para a Cafeteira, sem passar pela nuvem.
    // A duracao vem do proprio agendamento (#200); 0 cai no default da Cafeteira.
    g_agendador.set_on_fire([](const cafey::storage::Schedule& s) {
        g_cafeteira.post(cafey::core::Message{cafey::core::MessageType::StartBrew,
                                              static_cast<int32_t>(s.duration_s),
                                              static_cast<int32_t>(cafey::storage::EventOrigin::AGENDAMENTO)});
    });

    // `hora` dos agendamentos vem no fuso do dispositivo (FW-23); fuso
    // desconhecido cai em UTC com aviso.
    static const auto apply_timezone = [](const char* timezone) {
        int32_t offset_s = 0;
        if (!cafey::app::utc_offset_s(timezone, &offset_s)) {
            ESP_LOGW(TAG, "fuso '%s' desconhecido: agendamentos avaliados em UTC", timezone);
        }
        g_agendador.set_utc_offset(offset_s);
        ESP_LOGI(TAG, "fuso dos agendamentos: '%s' (offset %ld s)", timezone,
                 static_cast<long>(offset_s));
    };

    // Carrega os agendamentos persistidos para o Agendador executar offline.
    {
        static cafey::storage::Schedule loaded[cafey::storage::ScheduleStore::kMaxSchedules];
        const size_t n = schedule_store.count();
        for (size_t i = 0; i < n; ++i) loaded[i] = schedule_store.at(i);
        apply_timezone(schedule_store.timezone());
        g_agendador.set_schedules(loaded, n);
    }

    // SNTP em background: ao sincronizar, o Agendador passa a disparar por horario
    // e a Cafeteira marca os eventos com horario confiavel.
    static cafey::core::NtpSync ntp_sync([] {
        g_agendador.post(cafey::core::Message{cafey::core::MessageType::TimeSynced, 0});
        g_cafeteira.post(cafey::core::Message{cafey::core::MessageType::TimeSynced, 0});
    });

    // Handler de dominio do comando, compartilhado pelo downlink MQTT e pela
    // caracteristica BLE (spec §6.4: sem regra de negocio no transporte).
    static const auto handle_command = [](const cafey::app::CoffeeCommand& cmd) {
        switch (cmd.action) {
            case cafey::app::CoffeeAction::Ligar:
                g_cafeteira.post(Message{MessageType::StartBrew,
                                         static_cast<int32_t>(cmd.duracao_s),
                                         static_cast<int32_t>(cafey::storage::EventOrigin::APP)});
                break;
            case cafey::app::CoffeeAction::Desligar:
            case cafey::app::CoffeeAction::Cancelar:
                g_cafeteira.post(Message{MessageType::StopBrew, 0});
                break;
            case cafey::app::CoffeeAction::Unknown:
                break;
        }
    };

    // Lista retida de `agendamentos` vinda da nuvem: so aplica versao maior que
    // a gravada (o AWS IoT Core reentrega o retido a cada reconexao).
    static const auto apply_cloud_schedules = [](const std::string& payload) {
        static cafey::storage::Schedule list[cafey::storage::ScheduleStore::kMaxSchedules];
        size_t count = 0;
        cafey::app::SchedulePayloadMeta meta{};
        if (!cafey::app::parse_schedule_payload(
                payload, list, cafey::storage::ScheduleStore::kMaxSchedules, &count, &meta)) {
            return;
        }
        bool applied = false;
        esp_err_t err = schedule_store.replace_all_if_newer(list, count, meta.version, &applied,
                                                            meta.timezone.c_str());
        if (err != ESP_OK) {
            ESP_LOGE(TAG, "falha ao gravar agendamentos: %d", err);
            return;
        }
        if (!applied) {
            ESP_LOGI(TAG, "agendamentos versao %u ignorados (gravada: %u)",
                     static_cast<unsigned>(meta.version),
                     static_cast<unsigned>(schedule_store.version()));
            return;
        }
        apply_timezone(schedule_store.timezone());
        g_agendador.set_schedules(list, count);
        ESP_LOGI(TAG, "agendamentos versao %u aplicados: %u item(ns)",
                 static_cast<unsigned>(meta.version), static_cast<unsigned>(count));
        for (size_t i = 0; i < count; ++i) {
            ESP_LOGI(TAG, "  %02u:%02u dias=0x%02x ativo=%d duracao=%us",
                     list[i].hour, list[i].minute, list[i].days_of_week,
                     list[i].active ? 1 : 0, static_cast<unsigned>(list[i].duration_s));
        }
    };

    // MQTT/TLS ao AWS IoT Core (FW-21). Endpoint, client_id (= nome da thing) e
    // certificados vem da NVS (namespace cafey_mqtt, #169); sem eles o
    // dispositivo segue com BLE e preparo local.
    static cafey::core::MqttConfigStore mqtt_config_store;
    static cafey::core::MqttConfig mqtt_config;
    static cafey::core::EspMqttClient mqtt_client;
    if (mqtt_config_store.load(mqtt_config) == ESP_OK) {
        static const cafey::core::MqttTopics topics(mqtt_config.client_id);
        cafey::app::UplinkPublisher::make_last_will(topics, mqtt_config);

        mqtt_client.set_on_connected([] {
            g_conectividade.post(Message{MessageType::MqttConnected, 0});
        });
        mqtt_client.set_on_disconnected([] {
            g_conectividade.post(Message{MessageType::MqttDisconnected, 0});
        });
        mqtt_client.set_on_message([](const std::string& topic, const std::string& payload) {
            if (topic == topics.comando) {
                handle_command(cafey::app::parse_coffee_command(payload));
            } else if (topic == topics.agendamentos) {
                apply_cloud_schedules(payload);
            }
        });
        g_conectividade.attach_mqtt(&mqtt_client, &topics, [] {
            return mqtt_client.start(mqtt_config) == ESP_OK;
        });
        ESP_LOGI(TAG, "MQTT configurado para %s", mqtt_config.client_id.c_str());
    } else {
        ESP_LOGW(TAG, "sem credenciais MQTT na NVS (cafey_mqtt): seguindo offline");
    }

    // Estado da cafeteira -> topico `estado` (a Conectividade publica quando
    // houver conexao, e republica o corrente a cada CONNECT).
    g_cafeteira.set_on_state_change([](bool brewing) {
        g_conectividade.post(Message{MessageType::EstadoChanged, brewing ? 1 : 0});
    });

    // Servico BLE (FW-17, spec §6.4, UC-04 #249): comando/estado/agendamentos delegam para
    // os mesmos handlers de dominio do caminho MQTT — sem regra de negocio na
    // camada BLE. E o proxy da fila de eventos pendentes (FW-18, spec §6.5).
    static cafey::core::EspNimbleServer ble_server(cafey::app::BleService::kServiceUuid);
    static cafey::app::BleService ble_service(
        ble_server,
        [](const cafey::app::CoffeeCommand& cmd) { handle_command(cmd); },
        [](const cafey::storage::Schedule* list, size_t count,
           const cafey::app::SchedulePayloadMeta& meta) {
            // Mesmo caminho logico do downlink `agendamentos`: persiste e
            // recarrega o Agendador com a lista completa (idempotente).
            schedule_store.replace_all(list, count, meta.timezone.c_str());
            apply_timezone(schedule_store.timezone());
            g_agendador.set_schedules(list, count);
        },
        [](const std::string& ssid, const std::string& password) {
            wifi_manager.provision(ssid, password);
        });
    g_ble_service = &ble_service;
    static cafey::app::BleEventProxy ble_event_proxy(ble_server, event_queue_store);

    if (ble_service.begin("cafey")) {
        ble_event_proxy.begin();
        ESP_LOGI(TAG, "servico BLE ativo (comando/estado/agendamentos/wifi + proxy de eventos)");
    } else {
        ESP_LOGE(TAG, "falha ao iniciar o servico BLE");
    }

    g_conectividade.start();
    g_cafeteira.start();
    g_agendador.start();
    ntp_sync.start();

    ESP_LOGI(TAG, "pronto - AOs Cafeteira/Conectividade/Agendador iniciados");
}
