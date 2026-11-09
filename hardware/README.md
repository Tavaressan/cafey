# Módulo de Hardware — Cafey

Documentação técnica do esquemático elétrico, da pinagem e da arquitetura elétrica do módulo IoT para a cafeteira Britânia CP30.

> **Revisão B (09/11/2026, #304).** Componentes da montagem de 10/11/2026: SSR Fotek SSR-40DA (#305), fonte Apple A1385 (#306), disjuntor de 10 A (#307), botão RGB (#308) e rabicho do buzzer (#311). A revisão A (HL-52S, HLK-PM01, fusível, LED RGB discreto) está resumida na seção 7.
>
> **Este documento é projeto, não instrução de ligação à rede.** Nenhuma montagem em tensão de rede antes das validações de #305, #306 e #307 e da validação por profissional habilitado (#19). Ver a seção 5.

---

## 1. Arquitetura e visão geral

O módulo intercala a alimentação da cafeteira e controla a passagem de energia para a tomada por um relé de estado sólido (SSR) comandado pelo ESP32. A interface física é um único botão com anel de LED RGB.

### Diagrama de blocos

```mermaid
flowchart LR
    P1["P1<br/>Plugue NBR 14136"] -->|L_REDE| CB1["CB1<br/>Disjuntor 10 A<br/>(no tampo)"]
    CB1 -->|L_FONTE| SSR1["SSR1<br/>Fotek SSR-40DA<br/>+ dissipador"]
    CB1 -->|L_FONTE| PS1["PS1<br/>Apple A1385<br/>5 V / 1 A"]
    SSR1 -->|L_SAIDA| J1["J1<br/>Tomada NBR 14136<br/>(Britânia CP30)"]
    P1 -->|"N e PE (passa-através)"| J1
    PS1 -->|"cabo USB-A → micro-USB"| U1["U1<br/>ESP32 DevKit V1"]
    U1 -->|"GPIO26 (RELE_IN)"| SSR1
    SW1["SW1<br/>Botão RGB"] -->|"GPIO27 (BOTAO)"| U1
    U1 -->|"GPIO21/22/23 (LED_R/G/B)"| SW1
    U1 -->|"GPIO25 (BUZZER)"| J5["J5<br/>Rabicho do buzzer"]
```

### Diagrama de alimentação

```mermaid
flowchart TB
    Rede["Rede AC (L, N, PE)"] --> CB1["CB1 — disjuntor 10 A"]
    CB1 -->|"L_FONTE (L1 no esboço)"| SSR1["SSR1 — terminais 1 → 2"]
    SSR1 -->|L_SAIDA| Tomada["J1 — tomada da cafeteira"]
    Rede -->|"N, PE direto"| Tomada
    CB1 -->|"L_FONTE + N, direto nos pinos"| PS1["PS1 — Apple A1385<br/>100–240 V AC → 5 V / 1 A"]
    PS1 -->|"USB-A → cabo → micro-USB"| USB["DevKit: VBUS → diodo → barramento 5V/VIN"]
    USB --> LDO["Regulador da placa → +3V3"]
    LDO --> Logica["ESP32, R2 (pull-up do botão)"]
    USB --> LED["Anel RGB do botão (via GPIO e R3–R5)"]
```

- A proteção (CB1) fica **antes** do SSR e da derivação da fonte, a mesma filosofia do F1 da revisão A.
- O PE é passa-através do plugue até a tomada (borne J3). A carcaça é de PETG, sem massa metálica a aterrar.
- **Alimentação única:** o ESP32 é alimentado só pelo cabo USB. No esquemático, `+5V` representa o barramento 5V/VIN da placa, que recebe o VBUS do USB pelo diodo do DevKit. **Nada externo é ligado ao pino VIN**, e VIN e USB nunca alimentam a placa ao mesmo tempo (#306).
- **Tensão:** a CP30 é 127 V. O módulo só comuta a rede para a tomada, não converte tensão: um módulo bivolt não torna a cafeteira bivolt. Operação bivolt do conjunto só será declarada depois da validação elétrica (#19).

### Diagrama de conexões (fio a fio)

**Pendente.** Bitolas, emendas e bornes só serão desenhados depois de validar #305, #306 e #307, como pede a #304.

---

## 2. Esquemático KiCad (`hardware/`)

- Projeto: `hardware/pi_dsm_26_2.kicad_pro` (KiCad 10.0.x; última exportação com 10.0.7)
- Esquemático: `hardware/pi_dsm_26_2.kicad_sch` (revisão B, 09/11/2026)
- PDF vetorial: `hardware/pi_dsm_26_2.pdf`
- Bibliotecas locais: `hardware/libraries/` — `cafey.kicad_sym` traz os símbolos da revisão B (`SSR_DC_AC`, `Apple_A1385`, `Circuit_Breaker_1P`, `Botao_RGB`). `relay_module.kicad_sym` fica como histórico da revisão A.

### Referências dos componentes

| Ref | Função | Modelo / especificação | Situação |
|---|---|---|---|
| **U1** | Microcontrolador | ESP32 DevKit V1 (30 pinos), removível (#309) | Em uso |
| **PS1** | Fonte 5 V | Apple A1385, 100–240 V AC → 5 V / 1 A, saída USB-A. Rede ligada direto nos pinos (decisão do responsável) | Em uso; verificações em #306 |
| **SSR1** | Acionamento da carga | Fotek SSR-40DA: entrada 3–32 V DC (3 = +, 4 = −), saída 24–380 V AC (1, 2), com dissipador aletado | Em uso; capacidade, fuga e ensaio térmico em #305 |
| **CB1** | Proteção contra sobrecorrente | Disjuntor 10 A, alavanca acessível no tampo | Modelo e curva a registrar (#307) |
| **SW1** | Interface física | Botão momentâneo NA com anel de LED RGB. Unidade A = contato (GPIO27); unidade B = LED (GPIO21/22/23) | Modelo, polaridade do LED e resistor interno a registrar (#308) |
| **J5** | Rabicho do buzzer | Conector destacável 2 vias (JST-XH): 1 = BUZZER (GPIO25), 2 = GND | Buzzer e estágio de acionamento a definir (#311) |
| **P1** | Entrada de energia | Cabo macho NBR 14136, 3G1,0 mm² (10 A) | Em uso |
| **J1** | Saída para a cafeteira | Tomada fêmea NBR 14136 de painel (10 A) | Em uso |
| **J3** | Junção do PE | Borne de aterramento DIN verde-amarelo / WAGO 221-412 | Em uso |
| **R1** | Pull-down da entrada do SSR | 10 kΩ, 1/4 W, de `RELE_IN` para GND: GPIO em alta impedância = SSR desligado | Em uso (era pull-up na rev. A) |
| **R2** | Pull-up do botão | 10 kΩ, 1/4 W | Em uso |
| **R3 / R4 / R5** | Limitadores do anel RGB (R/G/B) | 220 Ω / 100 Ω / 100 Ω, 1/4 W | Valores da rev. A; confirmar ou dispensar conforme o botão (#308) |
| **C1** | Reserva do barramento 5 V | Eletrolítico 470 µF / 16 V | Mantido da rev. A |
| **C2** | Desacoplamento do barramento 5 V | Cerâmico 100 nF | Mantido da rev. A |
| **C3** | Debounce do botão | Cerâmico 100 nF | Em uso |

Saíram do esquemático: **U2** (HLK-PM01, substituído por PS1), **F1** (fusível, substituído por CB1), **K3** (módulo relé HL-52S, substituído por SSR1) e **D1** (LED RGB discreto, agora unidade B de SW1). O conector USB de painel (M8) foi descartado (#309).

---

## 3. Pinagem (fonte única)

Esta tabela é a referência para o esquemático, o firmware e a documentação. A pinagem antiga deste arquivo (LED em GPIO32/33/25) estava errada: o esquemático e o firmware sempre usaram GPIO21/22/23.

| Sinal | GPIO | Pino DevKit | Direção | Polarização / componente | Firmware |
|---|---|---|---|---|---|
| `RELE_IN` (SSR1 +) | GPIO26 | 7 | Saída | R1 10 kΩ **pull-down**; nível ALTO liga a carga | `Relay`, `Cafeteira::kRelayActiveLevel = High` |
| `BOTAO` (SW1 A) | GPIO27 | 6 | Entrada | R2 10 kΩ pull-up + C3 100 nF; solto = 1, pressionado = 0 | `Cafeteira` (botão) |
| `LED_R` (SW1 B) | GPIO21 | 26 | Saída (LEDC) | R3 | `RgbLed` |
| `LED_G` (SW1 B) | GPIO22 | 29 | Saída (LEDC) | R4 | `RgbLed` |
| `LED_B` (SW1 B) | GPIO23 | 30 | Saída (LEDC) | R5 | `RgbLed` |
| `BUZZER` (J5.1) | GPIO25 | 8 | Saída (LEDC, se passivo) | Estágio a definir (#311) | Ainda não usado |
| Sensor de temperatura do SSR (opcional) | GPIO33 | 9 | Entrada | Reservado, fora do esquemático (#323) | Ainda não usado |
| `+5V` (barramento 5V/VIN) | VIN | 1 | Alimentação | Vem do USB (PS1). Nada externo neste pino | — |
| `+3V3` | 3V3 | 16 | Alimentação (`power_out`) | Regulador da placa; alimenta R2 | — |
| `GND` | GND | 2, 17 | — | Comum DC, SSR1 −, J5.2 | — |

**Por que GPIO25 para o buzzer:** não é pino de strapping (0, 2, 5, 12, 15), não é somente entrada (34–39), não é a UART0 de gravação (1, 3) e não emite sinal no boot (como o GPIO14). Fica no mesmo lado do DevKit que GPIO26 e GPIO27, o que agrupa a fiação.

**Por que GPIO33 para o sensor (opcional):** é canal do ADC1, que continua disponível com o Wi-Fi ativo (o ADC2 não). Serve tanto para um NTC analógico quanto para um sensor 1-Wire (DS18B20). A escolha do sensor é do spike #323.

**Pinos evitados:** GPIO0, 2, 5, 12 e 15 (strapping; GPIO12 alto no boot impede a partida), GPIO1 e 3 (UART0), GPIO6–11 (flash), GPIO14 (sinal no boot) e GPIO34–39 (somente entrada).

---

## 4. Validação elétrica e relatório ERC

Verificação com `kicad-cli sch erc` (KiCad 10.0.7, 09/11/2026): **0 erros, 54 avisos**, todos esperados.

| Avisos | Tipo | Motivo |
|---|---|---|
| 20 | `pin_not_connected` | GPIOs livres do DevKit (eram 21; o GPIO25 passou a ser usado pelo buzzer) |
| 1 | `pin_to_pin` | U1.GND (bidirecional) × PS1.GND (saída de alimentação). Mesmo caso da rev. A com U2.−Vout: a fonte define a referência zero |
| 1 | `same_local_global_label` | Rótulo local `Earth_Protective` × símbolo de alimentação de mesmo nome. Herdado da rev. A, intencional (nó do PE) |
| 32 | `lib_symbol_issues` | Só em máquina sem as bibliotecas padrão do KiCad configuradas (`power`, `Device`, `Connector`, `Connector_Generic`). Os símbolos estão embutidos no esquemático; com as bibliotecas instaladas, o aviso some |

Netlist conferida nó a nó (`kicad-cli sch export netlist`):

| Rede | Nós |
|---|---|
| `L_REDE` | P1.L, CB1.1 |
| `L_FONTE` | CB1.2, PS1.AC/L, SSR1.1 (T1) |
| `L_SAIDA` | SSR1.2 (T2), J1.L |
| `N` | P1.N, PS1.AC/N, J1.N |
| `Earth_Protective` | P1.PE, J1.PE, J3 |
| `RELE_IN` | U1.GPIO26, SSR1.3 (+), R1 |
| `GND` | U1.GND (2, 17), PS1.GND, SSR1.4 (−), R1, SW1.2, SW1.K, C1, C2, C3, J5.2 |
| `+5V` | U1.VIN, PS1.VBUS, C1, C2 |
| `+3V3` | U1.3V3, R2 |
| `BOTAO` | U1.GPIO27, SW1.1, R2, C3 |
| `LED_R/G/B` | U1.GPIO21/22/23 → R3/R4/R5 → SW1.RA/GA/BA |
| `BUZZER` | U1.GPIO25, J5.1 |

### Comandos

```sh
kicad-cli sch erc hardware/pi_dsm_26_2.kicad_sch
kicad-cli sch export pdf -o hardware/pi_dsm_26_2.pdf hardware/pi_dsm_26_2.kicad_sch
kicad-cli sch export netlist -o hardware/pi_dsm_26_2.net hardware/pi_dsm_26_2.kicad_sch
```

---

## 5. Segurança elétrica — pendências

O esboço manuscrito do responsável (08/11/2026) foi o ponto de partida, **não** um esquemático validado. Status de cada verificação da #304:

| Verificação | Status | Onde |
|---|---|---|
| Autenticidade do SSR | Confirmada pelo responsável (08/11/2026) | #305 |
| Especificações da unidade SSR registradas (etiqueta) | Pendente | #305 |
| Capacidade do SSR para a carga real (~6,3 A em 127 V), com curva de redução | Pendente | #305 |
| Dissipação térmica e dissipador (estimativa ~8–10 W durante o preparo) | Pendente — ensaio térmico de 10/11/2026 | #305 |
| Corrente de fuga do SSR desligado e modo de falha (curto) | Pendente. Desligar pelo app **não** secciona a rede | #305 |
| Acionamento direto pelo GPIO (3,3 V contra mínimo de 3 V) | Pendente — medir na bancada | #305 |
| Dimensionamento do disjuntor e dos condutores | Pendente — dados da unidade | #307 |
| Proteção contra sobrecorrente (breaker protege a fiação num curto do SSR) | Pendente | #307, #19 |
| Isolamento entre baixa tensão e rede | Pendente — separação física na carcaça | #302 |
| Distâncias de isolamento e de escoamento | Pendente | #302, #19 |
| Aterramento (PE passa-através) e carcaça plástica | Projetado (J3); verificar na montagem | #19 |
| Isolamento físico da entrada de alimentação | Pendente | #302 |
| Integração do A1385 com ligação direta nos pinos | Decisão do responsável; verificações pendentes | #306 |
| Operação bivolt | Não declarada. Só depois da validação do conjunto | #19 |

A classificação de 40 A do SSR não significa que seja necessário ou seguro usar essa corrente.

---

## 6. Firmware

O firmware segue a pinagem da seção 3. A polaridade do SSR está em `Cafeteira::kRelayActiveLevel` (`firmware/main/app/cafeteira.hpp`). **O firmware atual não pode rodar com o módulo HL-52S ligado ao GPIO26:** o repouso do active-high (nível BAIXO) liga o HL-52S.

---

## 7. Revisão A (histórico)

Até 08/11/2026, o caminho da carga era `P1 → F1 (fusível 10 A T) → K3 (HL-52S, COM/NO, active-low) → J1`, com U2 (HLK-PM01) alimentando o VIN, R1 como pull-up para +3V3, LED RGB discreto D1 e botão M1. O esquemático, o PDF e este README dessa revisão estão no histórico do Git (commit `f5c82fb`).
