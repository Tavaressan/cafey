# Firmware — ESP32 (ESP-IDF C++17)

Firmware do módulo IoT da cafeteira Cafey desenvolvido em **C++17** sobre o framework oficial **ESP-IDF** (com FreeRTOS).

Fatia atual: **I/O local, Driver do Relé (com proteção anti-glitch no boot), Driver do LED RGB com cores de estado (spec §5.3) e módulo Wi-Fi/BLE (conexão, reconexão automática e provisionamento de credenciais — UC-04)**. Botão alterna o relé; LED RGB indica o estado do sistema.

## Pinagem Definida

| Sinal | GPIO | Configuração Elétrica | Observação |
|---|---|---|---|
| Relé (IN) | 26 | Gatilho de nível BAIXO — módulo HL-52S (LOW = ligado); R1 10 kΩ pull-up externo | O driver força nível ALTO antes de inicializar para impedir pulso espúrio no boot |
| Botão | 27 | R2 10 kΩ pull-up externo + C3 100 nF debounce RC | Solto = 1, pressionado = 0 (borda de descida = clique) |
| LED vermelho | 21 | R3 220 Ω (Cátodo comum) | Canal R (compõe Amarelo/Âmbar, Branco e Vermelho) |
| LED verde | 22 | R4 100 Ω (Cátodo comum) | Canal G (compõe Verde fixo/pulsando, Âmbar e Branco) |
| LED azul | 23 | R5 100 Ω (Cátodo comum) | Canal B (compõe Azul respirando quando ocioso online e Branco) |
| Buzzer passivo | 25 | Base de um NPN via resistor; buzzer entre +5 V e o coletor (ver [Buzzer](#buzzer-passivo-cafeydriversbuzzer--311)) | Tom por LEDC (timer 1 / canal 1). Duty 0 = silêncio |

## Estados do LED RGB (Spec §5.3)

> **Regra mental:** a **cor** indica o estado da cafeteira; o **piscar** indica problema de rede.

| Cor / Padrão | Significado | Estado Lógico |
|---|---|---|
| **Azul respirando (lento)** | Conectado e ocioso | `Idle` + `network_ok = true` |
| **Verde fixo** | Agendamento armado | `ScheduleArmed` + `network_ok = true` |
| **Âmbar fixo** | Desconectado, com agendamento armado | `ScheduleArmed` + `network_ok = false` |
| **Verde pulsando** | Preparando café agora | `Brewing` |
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
│   │   ├── rgb_led.hpp/.cpp # Driver do LED RGB com cores e padrões de estado (spec §5.3)
│   │   └── buzzer.hpp/.cpp  # Driver do buzzer passivo: padrões sonoros por LEDC (#311)
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
    ├── test_buzzer.cpp         # Testes unitários do driver do buzzer passivo (#311)
    ├── test_sound_settings_store.cpp # Testes do modo silencioso persistido em NVS (#311)
    ├── test_cafeteira.cpp      # Testes unitários do Active Object Cafeteira (TDD)
    ├── test_reconnect_policy.cpp    # Testes unitários do backoff de reconexão (TDD)
    └── test_wifi_credentials.cpp    # Testes unitários da persistência de credenciais (TDD)
```

## Driver do Relé (`cafey::drivers::Relay`)

- **Classe Orientada a Objetos**: RAII garantindo desligamento seguro no destrutor.
- **Proteção Anti-Glitch**: Força o pino em nível lógico `HIGH` (active-low OFF) antes e imediatamente após a configuração de saída do GPIO, atuando em conjunto com o resistor `R1` (10k pull-up) externo.
- **Métodos**: `init()`, `set(bool on)`, `turn_on()`, `turn_off()`, `toggle()`, `is_on()`, `is_initialized()`, `get_pin()`.
- **Semântica Move**: Proteção contra duplicação de posse do hardware físico via exclusão de cópia e suporte a movimentação (`move`).

## Driver do LED RGB (`cafey::drivers::RgbLed`) — Spec §5.3

- **Controle dos 3 Canais (RGB)**: GPIOs 21, 22 e 23, suportando cátodo comum (padrão) e ânodo comum.
- **Mapeamento de Padrões**: Solid (fixo), Blink (piscando a 1 Hz), Pulse (pulsando a 2 Hz) e Breathe (respiração lenta a cada 2,4 s).
- **Integração com o AO Cafeteira**: o método `Cafeteira::on_tick()` aciona `RgbLed::tick()` a cada 10 ms para manter a animação suave e em sincronia com o estado operacional e de rede da cafeteira.

## Buzzer passivo (`cafey::drivers::Buzzer`) — #311

O buzzer é **passivo**: o firmware gera a frequência de cada nota por PWM de hardware (LEDC, timer 1 / canal 1, 10 bits, duty de 50 %). Timer e canal são separados do canal azul do LED (timer 0 / canal 0).

### Circuito

Um buzzer passivo é uma bobina. A corrente dela passa do que um GPIO deve fornecer, por isso o GPIO aciona um transistor NPN como chave do lado baixo. O mesmo estágio está no esquemático rev. B (`hardware/`, PR #350), com as mesmas referências:

```
+5V ─────────────┬────────────┐
                 │            │
             BUZZER (+)   D1 1N4148
             BUZZER (−)   (cátodo no +5 V)
                 │            │
                 ├────────────┘
                 │ C
GPIO25 ─ R6 ─┬───┤ B   Q1 NPN (S8050, BC337 ou 2N2222)
        1 kΩ │   │ E
     R7 10 kΩ│   │
             │   │
GND ─────────┴───┘
```

| Ref. | Peça | Função |
|---|---|---|
| Q1 | NPN de uso geral com Ic ≥ 500 mA: S8050 ou 2N2222 (EBC), ou BC337 (CBE: conferir a pinagem) | Chave do buzzer. Evite o BC547 (100 mA) se a bobina tiver menos de ~50 Ω |
| R6 | 1 kΩ (470 Ω se a bobina tiver menos de 20 Ω) | Corrente de base: ~2,6 mA com 1 kΩ |
| R7 | 10 kΩ, base → GND | Mantém Q1 cortado enquanto o GPIO flutua no boot |
| D1 | 1N4148 em antiparalelo com o buzzer | Absorve o pico da bobina ao cortar |

- **Alimentação em +5 V** (barramento da A1385): mais volume, útil com o buzzer atrás da parede da carcaça. Em +3V3 também funciona, mais baixo.
- **Antes de ligar:** meça a resistência da bobina com o multímetro. A corrente de pico é cerca de 5 V ÷ R (16 Ω → ~300 mA; a média com duty de 50 % fica na metade). Se passar de ~200 mA, ponha um resistor de 22–47 Ω em série com o buzzer. O som mais longo (café pronto) dura ~1,3 s, então o impacto na fonte de 1 A é pequeno.
- **Conector J5** (rabicho): com Q1 na placa base, as duas vias passam a ser 1 = +5 V (buzzer +) e 2 = coletor de Q1 (buzzer −).
- **Polaridade:** o buzzer passivo costuma ter o + marcado na carcaça ou o terminal mais longo.

### Padrões e eventos

A tabela única `Buzzer::pattern_for` liga cada evento a um padrão, como `RgbLed::color_for` faz para o LED.

| Evento | Onde surge | Padrão | Som |
|---|---|---|---|
| Inicialização | `Cafeteira::on_start` | `Startup` | Arpejo ascendente curto |
| Rede conectada | `NetworkStatusChanged` 0 → 1 | `Connected` | Duas notas subindo |
| Falha de comunicação | `NetworkStatusChanged` 1 → 0 | `Disconnected` | Duas notas descendo |
| Janela de vínculo aberta | toque longo (FW-24) | `Binding` | Três bipes agudos |
| Preparo iniciado | botão, app ou agendamento | `Beep` | Bipe curto |
| Preparo cancelado | botão ou comando durante o preparo | `Cancel` | Duas notas descendo, curtas |
| Café pronto | `finish_brew` (CONCLUIDO) | `Ready` | Melodia |
| Falha operacional | `set_error(true)` | `Error` | Alternância grave |
| Som reativado | comando `ATIVAR_SOM` | `DoubleBeep` | Dois bipes |

- **Não bloqueia:** `play()` só arma a sequência; `Cafeteira::on_tick()` chama `Buzzer::tick()` a cada 10 ms, que troca de nota quando a atual termina. Não há `vTaskDelay`: comando, botão e LED seguem no mesmo ritmo com som tocando (teste `test_cafeteira_sound_never_delays_command_button_or_led`).
- **Prioridade:** aviso de rede < confirmações < café pronto e vínculo < erro. Um som menos importante não interrompe um mais importante; é descartado.
- **Rede:** só toca na mudança de estado e com intervalo mínimo de 60 s (`kNetworkSoundCooldownS`), para Wi-Fi ou MQTT instável não virar sequência de bipes.

### Modo silencioso

Persistido em NVS (namespace `cafey_som`, chave `mudo`) por `SoundSettingsStore`; sobrevive a reboot. Enquanto o app não tem a tela de preferências (#322), o modo muda pelo mesmo payload de comando do preparo, pela característica BLE de comando ou pelo tópico MQTT `comando`:

```json
{"acao":"SILENCIAR"}
{"acao":"ATIVAR_SOM"}
```

Na bancada, dá para mandar pelo nRF Connect na característica de comando do serviço BLE.

No Wokwi (`diagram.json`), o buzzer `bz1` está ligado direto no GPIO25, sem transistor: na simulação não há bobina para proteger.

## Módulo Wi-Fi (`cafey::core::WifiManager`) — UC-04

- **Conexão STA**: inicializa `esp_wifi` em modo estação sobre o event loop padrão do ESP-IDF (`esp_event`).
- **Reconexão automática**: ao receber `WIFI_EVENT_STA_DISCONNECTED`, agenda uma nova tentativa via `esp_timer` (sem bloquear a task do event loop) com backoff exponencial calculado por `ReconnectPolicy` (1 s → dobra a cada falha → teto de 60 s); o contador de tentativas e o atraso são resetados ao obter IP (`IP_EVENT_STA_GOT_IP`).
- **Provisionamento de credenciais**: `WifiManager::provision(ssid, password)` grava o SSID/senha via `WifiCredentialsStore` (namespace NVS `cafey_wifi`) e conecta imediatamente; no boot, se já houver credenciais gravadas, conecta direto — caso contrário, permanece em `WifiState::NOT_PROVISIONED` aguardando o app/BLE provisionar (a exposição via BLE é escopo de FW-17).
- `ReconnectPolicy` e `WifiCredentialsStore` são unidades puras/isoladas do hardware, com testes unitários de host (`test_reconnect_policy.cpp`, `test_wifi_credentials.cpp`); `WifiManager` em si integra diretamente com `esp_wifi`/`esp_event`/`esp_timer` e é validado pela build real do ESP-IDF (CI).

## Executar Testes Unitários Nativos (Host)

Os testes unitários (driver do relé, driver do LED RGB, driver do buzzer, preferência de som, Cafeteira AO, backoff de reconexão e persistência de credenciais Wi-Fi) podem ser compilados e executados em qualquer máquina de desenvolvimento (macOS / Linux) sem necessidade da toolchain física do ESP-IDF instalada:

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
