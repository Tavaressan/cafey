# Spike: Avaliação de Inclusão do Display OLED SSD1306 (I2C) no Módulo IoT

**Status:** Concluído  
**Autor:** Antigravity / Vetor  
**Data:** 26/10/2026  
**Issue relacionada:** #127 (`[SPIKE] Avaliar inclusão de display OLED SSD1306 (I2C) no módulo IoT`)  
**Label:** `corte-5` (Último a cair)  

---

## 1. Contexto e Motivação

Atualmente, o feedback visual local da cafeteira conectada Caféy é provido exclusivamente por um LED RGB de cátodo comum (especificação técnica §5.3):
- A **cor** indica o estado operacional da cafeteira (`Azul` = Ocioso/Conectado, `Âmbar` = Agendamento armado, `Verde` = Preparando, `Branco` = Pronto, `Vermelho` = Erro).
- O **padrão** indica problemas de conectividade (`Piscando` = Sem Wi-Fi/Nuvem).

Embora o LED RGB atenda ao requisito mínimo com baixo custo e simplicidade mecânica, ele possui limitações intrínsecas:
1. Impossibilidade de informar tempo restante de preparo sem consultar o aplicativo móvel.
2. Dificuldade de diagnóstico de rede em campo (SSID conectado, IP obtido, sincronização NTP e falhas de handshake MQTT/TLS) sem conexão serial com PC.
3. Ausência de suporte visual para provisionamento BLE (exibição de código OTP/PIN de pareamento).

Este spike analisa a viabilidade técnica, elétrica, mecânica, de firmware e de cronograma para inclusão do display OLED SSD1306 (0,96", 128x64, interface I2C de 4 pinos: VCC, GND, SCL, SDA), já disponível no kit de componentes do projeto.

---

## 2. Levantamento de Pinos e Conflitos de Hardware (ESP32 DevKit 30 pinos)

### 2.1 Conflito Identificado
Na configuração atual do firmware e esquemático KiCad:
- **GPIO 21**: Canal Vermelho (R) do LED RGB.
- **GPIO 22**: Canal Verde (G) do LED RGB.
- **GPIO 23**: Canal Azul (B) do LED RGB.
- **GPIO 26**: Saída de acionamento do relé (Active-LOW, pull-up R1 10 kΩ).
- **GPIO 27**: Entrada do botão frontal (pull-up R2 10 kΩ, filtro RC C3 100 nF).

O periférico padrão de I2C do ESP32 utiliza tradicionalmente **GPIO 21 (SDA)** e **GPIO 22 (SCL)**, gerando conflito direto com os canais Vermelho e Verde do LED.

### 2.2 Proposta de Pinagem I2C Livre de Conflitos
O ESP32 possui uma matriz de comutação de GPIOs (*GPIO Matrix*) que permite mapear os sinais do controlador de I2C mestre (`I2C_NUM_0`) para praticamente qualquer pino digital com capacidade de saída.

Pinos disponíveis e viáveis no ESP32 DevKit V1 (30 pinos):
- **Pinos proibidos para I2C:**
  - `GPIO 34, 35, 36, 39`: Pinos puramente de entrada (sem pull-up interno e sem circuito de saída).
  - `GPIO 0, 2, 12, 15`: Pinos de *strapping* (bootloader/flash mode; GPIO 12 em ALTO no boot causa falha de tensão no regulador interno de flash SPI).
  - `GPIO 1, 3`: UART0 (depuração USB e carga de firmware).

- **Alternativa Recomendada A (I2C remapeado via GPIO Matrix - Sem alterar LED):**
  - **I2C SDA:** `GPIO 18` (Pino 10 do DevKit)
  - **I2C SCL:** `GPIO 19` (Pino 9 do DevKit)
  - *Vantagem:* Mantém o circuito atual do LED RGB (`GPIO 21, 22, 23`) 100% inalterado na placa física e no esquemático existente. Ambos os pinos 18 e 19 são GPIOs universais sem restrições de strapping.

- **Alternativa Recomendada B (I2C nos pinos nativos 21/22 - Remapear LED):**
  - **I2C SDA:** `GPIO 21`
  - **I2C SCL:** `GPIO 22`
  - **LED R / G / B:** Mover para `GPIO 16, 17, 18` (ou usar transição para LED endereçável WS2812B em um único pino `GPIO 23`).

**Decisão de Pinagem:** A **Alternativa A (SDA em GPIO 18, SCL em GPIO 19)** é a mais segura, pois é 100% retrocompatível com o hardware já soldado e montado na bancada.

---

## 3. Orçamento Elétrico e Térmico

| Componente | Tensão | Corrente Média | Corrente de Pico |
|---|---|---|---|
| ESP32 DevKit V1 (ativo com Wi-Fi/BT) | 5 V (via VIN) | ~120 mA | ~240 mA (burst Tx) |
| Módulo de Relé 5V (bobina acionada) | 5 V (via VIN) | ~70 mA | ~80 mA |
| LED RGB (3 canais acesos a 3.3V) | 3.3 V | ~30 mA | ~45 mA |
| **Display OLED SSD1306 (0.96")** | **3.3 V** | **~15 mA (texto)** | **~25 mA (100% pixels acesos)** |
| **Total Estimado** | — | **~235 mA** | **~390 mA** |

- **Capacidade da Fonte HLK-PM01:** 5 V DC, 600 mA contínuos (3 W).
- **Margem de Segurança:** O pico máximo estimado com display OLED e relé acionado (~390 mA) consome apenas **65% da capacidade nominal** da fonte de bancada/embutida, mantendo mais de 200 mA de margem livre.
- **Regulador LDO 3.3V da placa (AMS1117-3.3):** Suporta até 800 mA - 1000 mA; o acréscimo de 15–25 mA no barramento de 3.3V é perfeitamente suportado com dissipação térmica negligenciável.

---

## 4. Impacto Mecânico e Construtivo

1. **Abertura Frontal:**
   - O módulo SSD1306 padrão possui placa de circuito impresso de $27{,}0 \times 27{,}0\text{ mm}$ com área visível ativa de $21{,}7 \times 10{,}8\text{ mm}$.
   - O projeto atual da carcaça metálica (chapa dobrada) precisará de uma janela retangular cortada a laser e furação de 4 pontos para parafusos M2 com espaçamento de $24 \times 24\text{ mm}$.
2. **Alternativa 3D:**
   - Criação de um *bezel* frontal em PETG/ABS que se encaixa no furo da chapa e prende o OLED por trás com garras de pressão ou insertos roscados M2.
3. **Cabeamento:**
   - Chicote adicional de 4 vias (3V3, GND, SCL, SDA) com conector JST-XH ou Dupont fêmea, exigindo roteamento sem interferir na gaveta de água ou no aquecimento da resistência da cafeteira (área quente).

---

## 5. Arquitetura de Software no Firmware

Para preservar o modelo determinístico de Active Objects do Cafey sem degradação do tempo de resposta do relé e do botão:
1. **Driver RAII C++:**
   - Criação de `cafey::drivers::DisplaySsd1306` em `firmware/main/drivers/display_ssd1306.hpp`.
   - Implementação modular sobre a API `driver/i2c_master.h` do ESP-IDF.
   - Testabilidade no host via `MockI2cBus` em `firmware/test/test_display_ssd1306.cpp`.
2. **Isolamento de Tarefas:**
   - O display NÃO deve rodar na task do Active Object do preparo (`CafeteiraAO`), pois a atualização do buffer de 1024 bytes (128x64 bits) a 400 kHz leva cerca de 25–35 ms via I2C.
   - Deve ser criada uma task dedicada de baixa prioridade (`DisplayTask`, prioridade 2 do FreeRTOS, período de 250 ms), recebendo eventos de estado via FreeRTOS Queue.
3. **Prevenção de Burn-in do OLED:**
   - Implementação de timeout de descanso de tela (*screen sleep*): display entra em standby após 120 segundos de inatividade da máquina, despertando com qualquer toque no botão ou comando via nuvem/app.

---

## 6. Priorização do Conteúdo Visual do Display

### MVP (Essencial para operação):
1. **Estado da Cafeteira:** Grande, legível a 1 metro (`OCIOSO`, `PREPARANDO...`, `PRONTO!`, `ERRO`).
2. **Contagem Regressiva:** Durante o preparo, contagem regressiva em segundos (`04:30` até `00:00`).
3. **Status de Conexão:** Ícones de Wi-Fi e Nuvem MQTT (conectado / sincronizado / offline).
4. **Próximo Agendamento:** Linha inferior com horário do próximo café programado (`Próx: 07:00`).

### Desejável / Pós-MVP:
5. **Diagnóstico IP:** Exibição do endereço IP local e intensidade de sinal RSSI no boot.
6. **Código de Pareamento BLE / OTP:** Exibição dinâmica do código de vínculo (#275).
7. **Contador de Xícaras:** Total acumulado e aviso de descalcificação quando atingir o limiar.

---

## 7. Estimativa de Esforço

| Frente | Atividades | Esforço Estimado |
|---|---|---|
| **Firmware** | Driver C++ SSD1306, mock de testes no host, task FreeRTOS, máquina de estados visual | 16h – 20h |
| **Hardware** | Revisão esquemático KiCad, roteamento dos pinos GPIO 18/19, prototipagem em protoboard | 6h – 8h |
| **Mecânica** | Ajuste do modelo CAD, recorte de chapa ou modelagem de moldura 3D para o OLED | 8h – 12h |
| **Documentação**| Atualização da especificação técnica (§4, §5.3, §9), diagramas de bloco e fotos | 4h – 6h |
| **Total** | — | **~34h a 46h de desenvolvimento** |

---

## 8. Recomendação Final

> **Recomendação:** **ADIAR A INTEGRAÇÃO FÍSICA PARA PÓS-BANCA (MANTER `corte-5`)**

### Justificativa Fundamentada:
1. **Prazos Acadêmicos e Entrega Crítica:** O projeto já possui validação física funcional com ESP32 real conectado à AWS IoT Core e acionando relé/LED RGB. A submissão do artigo EnGeTec e a apresentação para banca estão no cronograma imediato; retrabalhar a mecânica e a fiação física neste momento introduz risco de quebra de hardware funcional.
2. **Viabilidade Técnica Plena:** O spike comprovou que **não há impedimentos elétricos nem de pinos**: a fonte de 600 mA tem folga térmica de sobra e os pinos `GPIO 18` e `GPIO 19` permitem I2C perfeito sem tocar no circuito do LED RGB.
3. **Plano de Execução Quando Descongelado:**
   Caso a equipe decida integrar o display na versão final de demonstração de bancada, a issue deve ser desdobrada nas seguintes 4 frentes:
   - `[HW] Atualizar esquemático e cabeamento para I2C em GPIO 18 e 19`
   - `[MECH] Desenho de suporte frontal 3D para display OLED 0.96"`
   - `[FW] Implementar cafey::drivers::DisplaySsd1306 e task periódica FreeRTOS`
   - `[DOC] Atualizar especificação técnica §5.3 com layout da tela`
