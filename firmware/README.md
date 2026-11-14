# Firmware — ESP32 (ESP-IDF C++17)

Firmware do módulo IoT da cafeteira Cafey desenvolvido em **C++17** sobre o framework oficial **ESP-IDF** (com FreeRTOS).

Fatia atual: **I/O local, Driver do Relé (com proteção anti-glitch no boot), Driver do LED RGB com cores de estado (spec §5.3) e módulo Wi-Fi/BLE (conexão, reconexão automática e provisionamento de credenciais — UC-04)**. Botão alterna o relé; LED RGB indica o estado do sistema.

## Pinagem Definida

| Sinal | GPIO | Configuração Elétrica | Observação |
|---|---|---|---|
| SSR (entrada +) | 26 | Gatilho de nível ALTO — Fotek SSR-40DA (HIGH = ligado); R1 10 kΩ **pull-down** externo (HW-27, #305) | O driver força nível BAIXO antes e depois de configurar o GPIO para impedir pulso espúrio no boot. Polaridade em `Cafeteira::kRelayActiveLevel` |
| Botão | 27 | R2 10 kΩ pull-up externo + C3 100 nF debounce RC | Solto = 1, pressionado = 0 (borda de descida = clique) |
| Anel RGB do botão — R | 21 | R3 220 Ω (cátodo comum; confirmar no botão, #308) | Canal R (compõe Amarelo/Âmbar, Branco e Vermelho) |
| Anel RGB do botão — G | 22 | R4 100 Ω (cátodo comum; confirmar no botão, #308) | Canal G (compõe Verde fixo/pulsando, Âmbar e Branco) |
| Anel RGB do botão — B | 23 | R5 100 Ω (cátodo comum; confirmar no botão, #308) | Canal B (compõe Azul respirando quando ocioso online e Branco) |
| Buzzer (J5) | 25 | Estágio de acionamento a definir (#311) | Reservado; ainda não usado pelo firmware |
| Sensor de temperatura do SSR | 33 | Opcional (#323) | Reservado; ainda não usado pelo firmware |

> **Bancada (13/11/2026):** `Cafeteira::kRelayActiveLevel = Low` para o módulo HL-52S (LOW liga, R1 10 kΩ **pull-up** para +3V3). Para o SSR-40DA da montagem final, trocar para `High` e R1 para pull-down; a tabela de pinagem abaixo descreve o SSR.

Fonte única da pinagem: seção 3 de [`hardware/README.md`](../hardware/README.md). O botão RGB (#308) substitui o LED RGB discreto e o botão M1: contato no GPIO27 e anel nos GPIO21/22/23. O anel é de ânodo comum em 12 V, acionado por MOSFETs IRLZ44N no lado baixo (gate = GPIO via 220 Ω, pull-down de 10 kΩ): nível ALTO acende, então o driver segue com `common_anode = false`. O DHT11 está no GPIO33. O GPIO19 da placa de desenvolvimento em uso está com defeito e o projeto não o usa.

## Estados do LED RGB (Spec §5.3)

> **Regra mental:** a **cor** indica o estado da cafeteira; o **piscar** indica problema de rede.

| Cor / Padrão | Significado | Estado Lógico |
|---|---|---|
| **Azul respirando (lento)** | Conectado e ocioso | `Idle` + `network_ok = true` |
| **Verde fixo** | Agendamento armado | `ScheduleArmed` + `network_ok = true` |
| **Âmbar fixo** | Desconectado, com agendamento armado | `ScheduleArmed` + `network_ok = false` |
| **Verde pulsando** | Preparando café agora. Nos **últimos 60 s** (preparos de ≥ 2 min) pulsa mais rápido (4 Hz em vez de 2 Hz), para indicar que está acabando | `Brewing` |
| **Branco fixo (alguns minutos)** | Café pronto | `Ready` (recém-concluído) |
| **Amarelo piscando** | Sem Wi-Fi, ocioso (operando offline) | `Idle` + `network_ok = false` |
| **Vermelho fixo** | Erro ou falha | `Error` |

## Estrutura do Projeto

```
firmware/
├── CMakeLists.txt         # Configuração raiz CMake (C++17, IDF project)
├── partitions.csv         # Tabela de partições customizada (nvs, phy_init, factory, storage)
├── sdkconfig.defaults     # Configurações do ESP-IDF (C++, partições, FreeRTOS 1000Hz)
├── wokwi.toml             # Configuração do simulador Wokwi
├── diagram.json           # Esquemático de simulação Wokwi
├── main/
│   ├── CMakeLists.txt     # Registro do componente main e fontes C++
│   ├── main.cpp           # Ponto de entrada app_main (extern "C") e namespace cafey
│   ├── drivers/
│   │   ├── relay.hpp/.cpp # Driver de relé C++ com RAII e anti-glitch
│   │   └── rgb_led.hpp/.cpp # Driver do LED RGB com cores e padrões de estado (spec §5.3)
│   ├── app/
│   │   ├── cafeteira.hpp/.cpp # Active Object central da cafeteira (relé + botão + LED RGB)
│   │   ├── agendador.hpp/.cpp # Active Object do agendador offline (NTP + relógio interno)
│   │   └── conectividade.hpp/.cpp # Active Object de Wi-Fi, MQTT e drenagem de eventos
│   └── core/              # Estruturas e active objects centrais
│       ├── reconnect_policy.hpp/.cpp    # Backoff exponencial puro (cafey::core::ReconnectPolicy)
│       ├── wifi_credentials.hpp/.cpp    # Persistência NVS de SSID/senha (cafey::core::WifiCredentialsStore)
│       └── wifi_manager.hpp/.cpp        # Orquestração Wi-Fi STA via esp_wifi/esp_event (cafey::core::WifiManager)
└── test/
    ├── CMakeLists.txt          # Suíte de testes unitários nativos para host (C++17 / CTest)
    ├── mock_esp_gpio.hpp       # Mock da camada GPIO do ESP-IDF para testes sem hardware
    ├── mock_esp_nvs.hpp        # Mock da camada NVS do ESP-IDF para testes sem hardware
    ├── test_relay.cpp          # Testes unitários do driver do relé (TDD)
    ├── test_rgb_led.cpp        # Testes unitários do driver do LED RGB (spec §5.3)
    ├── test_cafeteira.cpp      # Testes unitários do Active Object Cafeteira (TDD)
    ├── test_reconnect_policy.cpp    # Testes unitários do backoff de reconexão (TDD)
    └── test_wifi_credentials.cpp    # Testes unitários da persistência de credenciais (TDD)
```

## Driver do Relé (`cafey::drivers::Relay`)

- **Classe Orientada a Objetos**: RAII garantindo desligamento seguro no destrutor.
- **Polaridade configurável** (HW-27, #305): `Relay::ActiveLevel::High` (padrão, SSR-40DA) ou `Relay::ActiveLevel::Low` (módulo HL-52S legado). O resistor externo `R1` precisa acompanhar a polaridade: **pull-down** para active-high, **pull-up** para active-low.
- **Proteção Anti-Glitch**: Força o pino no nível de repouso (desligado) antes e imediatamente após a configuração de saída do GPIO, atuando em conjunto com `R1`.
- **Atenção:** este firmware **não** pode rodar com o HL-52S ligado ao GPIO26. O repouso do active-high é nível BAIXO, que liga o HL-52S, então a cafeteira seria acionada no boot. Para voltar ao HL-52S, trocar `kRelayActiveLevel` para `Low` e R1 para pull-up.
- **Métodos**: `init()`, `set(bool on)`, `turn_on()`, `turn_off()`, `toggle()`, `is_on()`, `is_initialized()`, `get_pin()`.
- **Semântica Move**: Proteção contra duplicação de posse do hardware físico via exclusão de cópia e suporte a movimentação (`move`).

## Driver do LED RGB (`cafey::drivers::RgbLed`) — Spec §5.3

- **Controle dos 3 Canais (RGB)**: GPIOs 21, 22 e 23, suportando cátodo comum (padrão) e ânodo comum. Com os MOSFETs de lado baixo do anel de 12 V o padrão se mantém: o ânodo comum fica no 12 V e a inversão acontece no hardware.
- **Mapeamento de Padrões**: Solid (fixo), Blink (piscando a 1 Hz), Pulse (pulsando a 2 Hz) e Breathe (respiração lenta a cada 2,4 s).
- **Integração com o AO Cafeteira**: o método `Cafeteira::on_tick()` aciona `RgbLed::tick()` a cada 10 ms para manter a animação suave e em sincronia com o estado operacional e de rede da cafeteira.

## Driver do DHT11 (`cafey::drivers::Dht11`) e AO Ambiente

- **Leitura por fio único** no GPIO33 (dreno aberto + pull-up): pedido de 20 ms, resposta de 40 bits medidos em microssegundos, com a janela de bits (~4 ms) em seção crítica.
- **Decodificação pura e testada** (`pulses_to_frame`, `decode_frame`): soma de verificação, faixas (−40..80 °C, 0..100 %), temperatura negativa e a variante com casa decimal. Temperatura em décimos de grau (`240` = 24,0 °C).
- **`app::Ambiente`** lê a cada 5 s e reporta só quando o valor muda ou a cada 1 min. Após 3 falhas seguidas reporta "indisponível" uma vez e para de afirmar o último valor.
- **Telemetria:** a `Conectividade` põe `temperaturaC` e `umidade` na `saude` (`{"online":true,"temperaturaC":24.5,"umidade":55}`). Sem leitura válida o payload sai sem esses campos e o backend apaga o valor. O backend persiste em `dispositivos` (V17) e o diagnóstico do app mostra temperatura, umidade e a idade da leitura.
- Exatidão do DHT11: ±2 °C e resolução de 1 °C. É uma medição do ar interno da carcaça, não do dissipador.

## Módulo Wi-Fi (`cafey::core::WifiManager`) — UC-04

- **Conexão STA**: inicializa `esp_wifi` em modo estação sobre o event loop padrão do ESP-IDF (`esp_event`).
- **Reconexão automática**: ao receber `WIFI_EVENT_STA_DISCONNECTED`, agenda uma nova tentativa via `esp_timer` (sem bloquear a task do event loop) com backoff exponencial calculado por `ReconnectPolicy` (1 s → dobra a cada falha → teto de 60 s); o contador de tentativas e o atraso são resetados ao obter IP (`IP_EVENT_STA_GOT_IP`).
- **Provisionamento de credenciais**: `WifiManager::provision(ssid, password)` grava o SSID/senha via `WifiCredentialsStore` (namespace NVS `cafey_wifi`) e conecta imediatamente; no boot, se já houver credenciais gravadas, conecta direto — caso contrário, permanece em `WifiState::NOT_PROVISIONED` aguardando o app/BLE provisionar (a exposição via BLE é escopo de FW-17).
- `ReconnectPolicy` e `WifiCredentialsStore` são unidades puras/isoladas do hardware, com testes unitários de host (`test_reconnect_policy.cpp`, `test_wifi_credentials.cpp`); `WifiManager` em si integra diretamente com `esp_wifi`/`esp_event`/`esp_timer` e é validado pela build real do ESP-IDF (CI).

## Executar Testes Unitários Nativos (Host)

Os testes unitários (driver do relé, driver do LED RGB, Cafeteira AO, backoff de reconexão e persistência de credenciais Wi-Fi) podem ser compilados e executados em qualquer máquina de desenvolvimento (macOS / Linux) sem necessidade da toolchain física do ESP-IDF instalada:

```sh
cd firmware/test
mkdir -p build && cd build
cmake ..
make
ctest --output-on-failure
```

## Simulação no Wokwi (wokwi.com)

1. Abra o Wokwi para ESP32 ESP-IDF (<https://wokwi.com/projects/new/esp-idf-esp32>).
2. Adicione os fontes de `firmware/` (`main.cpp`, `drivers/relay.hpp`, `drivers/relay.cpp`, `drivers/rgb_led.hpp`, `drivers/rgb_led.cpp`, etc.).
3. Cole o conteúdo de `diagram.json` e `wokwi.toml`.
4. Clique em **Run**. O log serial reportará o boot com o LED indicando o estado correspondente e ao clicar no botão `SW1`, o relé e o LED alternam o estado.

### Simulação headless via `wokwi-cli` (CI local / validação sem hardware)

```sh
curl -L https://wokwi.com/ci/install.sh | sh   # instala em ~/bin/wokwi-cli
export WOKWI_CLI_TOKEN=<seu-token>             # gerado em https://wokwi.com/dashboard/ci
cd firmware
. ~/.espressif/v6.1/esp-idf/export.sh && idf.py build   # gera build/cafey_firmware.elf
wokwi-cli --serial-log-file /tmp/serial.log .            # lê wokwi.toml automaticamente
```

## Compilação Local com ESP-IDF (≥ 5.x)

> **Gravação na placa montada (revisão B, #309):** a carcaça não tem USB externo.
> Antes de ligar o USB ao computador: tirar o módulo da tomada, desconectar o
> cabo da fonte A1385 do micro-USB do ESP32 e retirar a placa da carcaça.
> Procedimento completo em `docs/status-modulo-fisico.md` ("Revisão B: sem USB
> de painel").

```sh
. $IDF_PATH/export.sh
idf.py set-target esp32
idf.py build
idf.py -p <porta> flash monitor
```
