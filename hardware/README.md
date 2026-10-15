# Módulo de Hardware — Cafey

Documentação técnica do esquemático elétrico, dimensionamento e arquitetura física do módulo IoT para a cafeteira Britânia CP30.

---

## 1. Arquitetura e Visão Geral

O módulo intercala a alimentação da cafeteira (127 Vac / 60 Hz), oferecendo controle liga/desliga automatizado, sensoriamento de status via LED RGB e interface física por botão metálico de painel.

```
       [Rede 127 Vac] (P1 - Cabo NBR 14136)
             │
             ├── Fase (L) ──> [Fusível F1: 10 A T] ──┬──> [Relé K3: HL-52S (COM/NO)] ──> [Tomada J1: Cafeteira]
             │                                       │
             │                                       └──> [Fonte AC/DC U2: HLK-PM01]
             │                                                  │
             │                                                  ├── +5V (VCC do Relé)
             │                                                  └── GND
             │                                                       │
             ├── Neutro (N) ─────────────────────────────────────────┴──> [Tomada J1: Cafeteira]
             │
             └── Terra (PE) ──> [Borne DIN / WAGO 221 (J3)] ────────────> [Tomada J1: Cafeteira]
```

### Características Elétricas
- **Tensão de entrada:** 127 Vac nominal (compatível com 220 Vac).
- **Consumo do módulo (standby/conectado):** < 1 W (< 200 mA em 5 V).
- **Proteção primária:** Fusível retardado $F_1 = 10\text{ A T / 250 V}$ (dimensione entre a cafeteira $\approx 6{,}3\text{ A}$ e o contato do relé $10\text{ A}$).
- **Conversão AC/DC:** Módulo Hi-Link HLK-PM01 (100–240 Vac $\to$ 5 Vdc / 600 mA isolado).
- **Isolamento de terra:** Gabinete termoplástico (PETG FDM). Condutor de Proteção (PE) interligado por borne passa-através direto para o pino terra da cafeteira.
- **Relé:** Módulo 2 canais LAFVIN (HL-52S com optoacoplador, active-LOW), canal 1 em uso.
- **Microcontrolador:** ESP32 DevKit V1 (30 pinos), alimentado em 5 V (pino VIN) com LDO interno gerando o barramento $3{,}3\text{ V}$.

---

## 2. Esquemático KiCad (`hardware/`)

O projeto elétrico foi desenhado no **KiCad 10.0.5**:
- Projeto: `hardware/pi_dsm_26_2.kicad_pro`
- Esquemático: `hardware/pi_dsm_26_2.kicad_sch`
- PDF vetorial: `hardware/pi_dsm_26_2.pdf`
- Biblioteca local de símbolos: `hardware/libraries/`

### Referências dos Componentes

| Ref | Descrição | Modelo / Especificação |
|---|---|---|
| **U1** | Microcontrolador IoT | ESP32 DevKit V1 (30 pinos, Wi-Fi 802.11 b/g/n + BLE 4.2) |
| **U2** | Fonte chaveada AC/DC isolada | Hi-Link HLK-PM01 (Entrada 100-240 Vac, Saída 5 Vdc / 3 W) |
| **K3** | Módulo de acionamento de potência | Módulo Relé 5 V 2 canais HL-52S (Gatilho nível baixo / Active-LOW) |
| **F1** | Proteção contra sobrecorrente | Fusível cerâmico/vidro 10 A T (retardado) 5×20 mm em porta-fusível in-line F-103C |
| **P1** | Entrada de energia | Cabo de força tripolar NBR 14136 macho 3G1,0 mm² (10 A) |
| **J1** | Saída para a cafeteira | Tomada fêmea NBR 14136 3 pinos (10 A) de painel |
| **J3** | Ponto de junção do condutor PE | Borne de aterramento DIN verde-amarelo / Conector WAGO 221-412 |
| **SW1** | Botão físico de comando | Botão metálico momentâneo NA 16 mm com LED anel |
| **D1** | Sinalização de estado do sistema | LED RGB 5 mm difuso, cátodo comum |
| **R1** | Pull-up do comando do relé | 10 kΩ, 1/4 W (mantém nível alto e relé desligado durante boot/alta impedância) |
| **R2** | Pull-up do botão físico | 10 kΩ, 1/4 W |
| **R3** | Resistor limitador LED Vermelho | 220 Ω, 1/4 W |
| **R4** | Resistor limitador LED Verde | 100 Ω, 1/4 W |
| **R5** | Resistor limitador LED Azul | 100 Ω, 1/4 W |
| **C1** | Filtro do barramento 5 V | Eletrolítico 470 µF / 16 V (reduz ondulação e sustenta picos de Wi-Fi) |
| **C2** | Desacoplamento de alta frequência | Cerâmico 100 nF no barramento 5 V |
| **C3** | Debounce analógico do botão | Cerâmico 100 nF em paralelo com SW1 |

---

## 3. Pinagem do ESP32 DevKit V1

| Pino DevKit | GPIO | Função no Cafey | Observações |
|---|---|---|---|
| Pin 1 | VIN | Entrada de alimentação 5 Vdc | Alimentado pelo HLK-PM01 (+5V) |
| Pin 2 | GND | Referência comum DC | Ligado ao negativo da fonte e periféricos |
| Pin 6 | GPIO27 | Entrada digital do botão (SW1) | Pull-up R2 (10 kΩ) + capacitor C3 (100 nF) |
| Pin 7 | GPIO26 | Saída digital de controle do relé (K3.IN) | Pull-up R1 (10 kΩ) para +3V3; nível LOW liga o relé |
| Pin 8 | GPIO25 | Saída PWM canal Azul do LED RGB | Limitador R5 (100 Ω) |
| Pin 9 | GPIO33 | Saída PWM canal Verde do LED RGB | Limitador R4 (100 Ω) |
| Pin 10 | GPIO32 | Saída PWM canal Vermelho do LED RGB | Limitador R3 (220 Ω) |
| Pin 16 | 3.3V | Saída regulada $3{,}3\text{ V}$ (`power_out`) | Alimenta os resistores de pull-up R1 e R2 |

> [!NOTE]
> Pinos com restrições de inicialização (strapping pins: GPIO0, GPIO2, GPIO12, GPIO15) e pinos apenas de entrada (GPIO34–39) foram intencionalmente evitados no projeto para garantir boot seguro e sem acionamento espúrio da carga.

---

## 4. Validação Elétrica e Relatório ERC (KiCad)

A integridade do esquemático é verificada via `kicad-cli sch erc`.

### Resultados do ERC
- **Erros:** 0
- **Advertências esperadas / Documentadas (HW-09 / #11):**
  1. `21 × pin_not_connected`: Pinos livres do DevKit V1 (GPIOs não utilizados).
  2. `1 × pin_to_pin`: Conexão entre U1.GND (`Bidirectional`) e U2.-Vout (`Power output`). Comportamento padrão em fontes AC/DC encapsuladas para estabelecer a referência zero do circuito DC.
- **Conexão de alimentação (HW-18 / #20):** O pino 16 do `ESP32_30Pin` está tipado como `power_out`, eliminando a necessidade de `PWR_FLAG` artificial no barramento $3{,}3\text{ V}$.
- **Netlist:** Exportada e auditada nó a nó via `kicad-cli sch export netlist`.

### Comandos de Inspeção Headless

```powershell
# Executar ERC
kicad-cli sch erc hardware/pi_dsm_26_2.kicad_sch

# Exportar PDF atualizado do esquemático
kicad-cli sch export pdf -o hardware/pi_dsm_26_2.pdf hardware/pi_dsm_26_2.kicad_sch

# Exportar Netlist
kicad-cli sch export netlist -o hardware/pi_dsm_26_2.net hardware/pi_dsm_26_2.kicad_sch
```
