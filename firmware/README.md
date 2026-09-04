# Firmware — ESP32 (ESP-IDF C++17)

Firmware do módulo IoT da cafeteira Cafey desenvolvido em **C++17** sobre o framework oficial **ESP-IDF** (com FreeRTOS).

Fatia atual: **I/O local, Driver do Relé (com proteção anti-glitch no boot) e módulo Wi-Fi (conexão, reconexão automática e provisionamento de credenciais — UC-04)**. Botão alterna o relé; LED RGB indica o estado.

## Pinagem Definida

| Sinal | GPIO | Configuração Elétrica | Observação |
|---|---|---|---|
| Relé (IN) | 26 | Gatilho de nível ALTO (HIGH = ligado); R1 10 kΩ pull-down externo | O driver força nível BAIXO antes de inicializar para impedir pulso espúrio no boot |
| Botão | 27 | R2 10 kΩ pull-up externo + C3 100 nF debounce RC | Solto = 1, pressionado = 0 (borda de descida = clique) |
| LED vermelho | 21 | R3 220 Ω (Cátodo comum) | Aceso = cafeteira desligada |
| LED verde | 22 | R4 100 Ω (Cátodo comum) | Aceso = cafeteira ligada |
| LED azul | 23 | R5 100 Ω (Cátodo comum) | Aceso durante o boot |

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
│   │   ├── relay.hpp      # Header do driver de relé C++ com RAII e anti-glitch
│   │   └── relay.cpp      # Implementação do driver cafey::drivers::Relay
│   └── core/              # Estruturas e active objects centrais
│       ├── reconnect_policy.hpp/.cpp    # Backoff exponencial puro (cafey::core::ReconnectPolicy)
│       ├── wifi_credentials.hpp/.cpp    # Persistência NVS de SSID/senha (cafey::core::WifiCredentialsStore)
│       └── wifi_manager.hpp/.cpp        # Orquestração Wi-Fi STA via esp_wifi/esp_event (cafey::core::WifiManager)
└── test/
    ├── CMakeLists.txt          # Suíte de testes unitários nativos para host (C++17 / CTest)
    ├── mock_esp_gpio.hpp       # Mock da camada GPIO do ESP-IDF para testes sem hardware
    ├── mock_esp_nvs.hpp        # Mock da camada NVS do ESP-IDF para testes sem hardware
    ├── test_relay.cpp          # Testes unitários do driver do relé (TDD)
    ├── test_reconnect_policy.cpp    # Testes unitários do backoff de reconexão (TDD)
    └── test_wifi_credentials.cpp    # Testes unitários da persistência de credenciais (TDD)
```

## Driver do Relé (`cafey::drivers::Relay`)

- **Classe Orientada a Objetos**: RAII garantindo desligamento seguro no destrutor.
- **Proteção Anti-Glitch**: Força o pino em nível lógico `LOW` antes e imediatamente após a configuração de saída do GPIO, atuando em conjunto com o resistor `R1` (10k pull-down) externo.
- **Métodos**: `init()`, `set(bool on)`, `turn_on()`, `turn_off()`, `toggle()`, `is_on()`, `is_initialized()`, `get_pin()`.
- **Semântica Move**: Proteção contra duplicação de posse do hardware físico via exclusão de cópia e suporte a movimentação (`move`).

## Módulo Wi-Fi (`cafey::core::WifiManager`) — UC-04

- **Conexão STA**: inicializa `esp_wifi` em modo estação sobre o event loop padrão do ESP-IDF (`esp_event`).
- **Reconexão automática**: ao receber `WIFI_EVENT_STA_DISCONNECTED`, agenda uma nova tentativa via `esp_timer` (sem bloquear a task do event loop) com backoff exponencial calculado por `ReconnectPolicy` (1 s → dobra a cada falha → teto de 60 s); o contador de tentativas e o atraso são resetados ao obter IP (`IP_EVENT_STA_GOT_IP`).
- **Provisionamento de credenciais**: `WifiManager::provision(ssid, password)` grava o SSID/senha via `WifiCredentialsStore` (namespace NVS `cafey_wifi`) e conecta imediatamente; no boot, se já houver credenciais gravadas, conecta direto — caso contrário, permanece em `WifiState::NOT_PROVISIONED` aguardando o app/BLE provisionar (a exposição via BLE é escopo de FW-17).
- `ReconnectPolicy` e `WifiCredentialsStore` são unidades puras/isoladas do hardware, com testes unitários de host (`test_reconnect_policy.cpp`, `test_wifi_credentials.cpp`); `WifiManager` em si integra diretamente com `esp_wifi`/`esp_event`/`esp_timer` e é validado pela build real do ESP-IDF (CI).

## Executar Testes Unitários Nativos (Host)

Os testes unitários (driver do relé, backoff de reconexão e persistência de credenciais Wi-Fi) podem ser compilados e executados em qualquer máquina de desenvolvimento (macOS / Linux) sem necessidade da toolchain física do ESP-IDF instalada:

```sh
cd firmware/test
mkdir -p build && cd build
cmake ..
make
ctest --output-on-failure
```

## Simulação no Wokwi (wokwi.com)

1. Abra o Wokwi para ESP32 ESP-IDF (<https://wokwi.com/projects/new/esp-idf-esp32>).
2. Adicione os fontes de `firmware/` (`main.cpp`, `drivers/relay.hpp`, `drivers/relay.cpp`).
3. Cole o conteúdo de `diagram.json` e `wokwi.toml`.
4. Clique em **Run**. O log serial reportará o boot com LED azul e ao clicar no botão `SW1`, o relé e o LED alternam o estado.

## Compilação Local com ESP-IDF (≥ 5.x)

```sh
. $IDF_PATH/export.sh
idf.py set-target esp32
idf.py build
idf.py -p <porta> flash monitor
```

### Evitando "idf.py: command not found"

- **`. export.sh` não persiste entre comandos de shell separados.** Cada terminal/execução novos
  precisa dar `source` de novo — não é algo que se "instala" uma vez. Se for automatizar (script,
  CI local, agente), o `source` e o `idf.py` têm que estar na **mesma** invocação de shell:
  ```sh
  . "$HOME/.espressif/v6.1/esp-idf/export.sh" && idf.py build
  ```
- **Instalação incompleta é a causa mais comum.** O toolchain (compiladores, cmake, ninja) pode já
  estar baixado em `~/.espressif/<versão>/` sem o venv Python ter sido criado — nesse caso
  `export.sh` falha com `ESP-IDF Python virtual environment ... not found`. Reinstale com:
  ```sh
  bash ~/.espressif/<versão>/esp-idf/install.sh esp32
  ```
- **A build real (`idf.py build`) é a única que valida o firmware de verdade.** A suíte de host
  (seção acima) cobre lógica pura via mocks, mas não pega problemas específicos do ESP-IDF (headers
  ausentes, `REQUIRES` de componente faltando, mudança de struct entre versões) — só o CI (Docker
  `espressif/idf:v5.3`) ou uma instalação local do ESP-IDF pegam isso. Se o Docker também estiver
  indisponível localmente (`docker info` falhando), a build real via `idf.py` é o único jeito de
  validar antes do CI.
- **Divergência de versão local vs. CI é uma fonte real de falso-negativo/falso-positivo.** O CI
  fixa `espressif/idf:v5.3`; uma instalação local em outra versão (ex. v6.1) pode expor mudanças de
  API/struct que não existem no v5.3 (e vice-versa). Ao investigar um erro de build que só aparece
  local ou só no CI, confira a versão primeiro (`idf.py --version`) antes de assumir que é bug de
  código.
