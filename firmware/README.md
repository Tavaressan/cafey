# Firmware — ESP32 (ESP-IDF C++17)

Firmware do módulo IoT da cafeteira Cafey desenvolvido em **C++17** sobre o framework oficial **ESP-IDF** (com FreeRTOS).

Fatia atual: **I/O local e Driver do Relé (com proteção anti-glitch no boot)**. Botão alterna o relé; LED RGB indica o estado.

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
└── test/
    ├── CMakeLists.txt     # Suíte de testes unitários nativos para host (C++17 / CTest)
    ├── mock_esp_gpio.hpp  # Mock da camada GPIO do ESP-IDF para testes sem hardware
    └── test_relay.cpp     # Testes unitários do driver do relé (TDD)
```

## Driver do Relé (`cafey::drivers::Relay`)

- **Classe Orientada a Objetos**: RAII garantindo desligamento seguro no destrutor.
- **Proteção Anti-Glitch**: Força o pino em nível lógico `LOW` antes e imediatamente após a configuração de saída do GPIO, atuando em conjunto com o resistor `R1` (10k pull-down) externo.
- **Métodos**: `init()`, `set(bool on)`, `turn_on()`, `turn_off()`, `toggle()`, `is_on()`, `is_initialized()`, `get_pin()`.
- **Semântica Move**: Proteção contra duplicação de posse do hardware físico via exclusão de cópia e suporte a movimentação (`move`).

## Executar Testes Unitários Nativos (Host)

Os testes unitários do driver podem ser compilados e executados em qualquer máquina de desenvolvimento (macOS / Linux) sem necessidade da toolchain física do ESP-IDF instalada:

```sh
cd firmware/test
mkdir -p build && cd build
cmake ..
make
ctest --output-on-failure
./test_relay
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
