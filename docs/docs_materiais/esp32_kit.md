# Kit ESP32 Basic Starter Kit — Inventário e Referência Técnica

> Documento de referência do hardware disponível para o projeto **Cafeteira Conectada (Cafey)**.
> Fonte primária: manual `ESP32 Basic Starter Kit — V2.0.23.10.11` (137 páginas), fornecido com o kit.
> Unidades em posse: **2 kits idênticos**.

---

## 1. Identificação e escopo

| Item | Valor | Natureza |
|---|---|---|
| Nome no manual | ESP32 Basic Starter Kit | Fato (capa do manual) |
| Versão do manual | V2.0.23.10.11 | Fato (capa do manual) |
| Fabricante/distribuidor | LAFVIN | Inferência — não consta no texto do manual; identificação vem da embalagem física |
| Placa de referência | ESP32 DEVKIT V1 (DOIT), versão de **30 pinos** | Fato (p. 4–5) |
| Projetos guiados | 10 | Fato (sumário) |
| Quantidade de kits | 2 | Informado pelo usuário |

### Origem do inventário

A página **Packing List (p. 1/137) do PDF é apenas imagem, sem camada de texto** — a extração textual não a recupera. O inventário da §2 foi transcrito da **captura da página impressa**, fornecida em 2026-09-08. A lista é agora **fato documental**, não mais inferência.

---

## 2. Inventário oficial (Packing List, p. 1/137)

21 itens de linha. Quantidades transcritas literalmente da lista do fabricante.

| # | Componente | Qtd./kit | Total (2 kits) |
|---|---|---|---|
| 1 | ESP32 Development Board | 1 | 2 |
| 2 | Display OLED 0,96" (SSD1306, 128×64, I²C) | 1 | 2 |
| 3 | Protoboard 830 pontos (MB-102, tamanho completo) | 1 | 2 |
| 4 | Módulo de desvio de obstáculo (IR) | 1 | 2 |
| 5 | Módulo de resistor fotossensível (LDR) | 1 | 2 |
| 6 | Módulo DHT11 (temperatura e umidade) | 1 | 2 |
| 7 | Sensor de movimento PIR HC-SR501 | 1 | 2 |
| 8 | Potenciômetro 10 kΩ | 1 | 2 |
| 9 | Cabo micro-USB | 1 | 2 |
| 10 | **Resistor 220 Ω** | **30** | **60** |
| 11 | Buzzer passivo | 1 | 2 |
| 12 | Buzzer ativo | 1 | 2 |
| 13 | **Módulo relé 5 V, 2 canais** | 1 | 2 |
| 14 | Chave de botão (tactile switch) | 6 | 12 |
| 15 | Cabo DuPont F-M | 10 | 20 |
| 16 | Cabo DuPont F-F | 10 | 20 |
| 17 | Cabo DuPont M-M | 10 | 20 |
| 18 | LED vermelho 5 mm | 5 | 10 |
| 19 | LED amarelo 5 mm | 5 | 10 |
| 20 | LED verde 5 mm | 5 | 10 |
| 21 | **LED RGB (cátodo comum)** | 2 | 4 |

### 2.1 Achados críticos da lista

**A1 — Não há resistor de 10 kΩ no kit.** O único valor fornecido é 220 Ω (30 un.). Os Projetos 1 e 8 do próprio manual pedem `10k Ohm resistor` para o botão; o kit não o fornece. O potenciômetro de 10 kΩ (item 8) é um componente distinto e não substitui um resistor fixo em divisor de tensão.

> **Impacto no Cafey**: o net `BOTAO` do esquemático KiCad depende de um pull-up. Duas saídas, em ordem de simplicidade:
>
> 1. **Pull-up interno do ESP32** (`INPUT_PULLUP` / `gpio_pullup_en`) — elimina o componente, o custo e o ponto de solda. O ESP32 tem pull-ups internos em todos os GPIOs exceto 34–39. Solução mais simples, sem compra.
> 2. **Resistor externo de 10 kΩ** — exige aquisição, mas dá valor determinístico (o pull-up interno do ESP32 é especificado em faixa ampla, tipicamente dezenas de kΩ) e resistência a ruído em cabo longo.
>
> A decisão é do projeto; a alternativa mais simples fica sinalizada. Enquanto não for decidida, o protótipo de bancada pode usar o pull-up interno sem bloquear a Seção 4 do artigo.

**A2 — Dois módulos não cobertos pelo manual.** O módulo de desvio de obstáculo (IR) e o módulo de resistor fotossensível (LDR) constam na lista mas **não aparecem em nenhum dos 10 projetos** do manual. Não têm aplicação prevista no Cafey; ficam como reserva.

**A3 — Divergência D2 resolvida.** Não existe resistor de 200 Ω no kit. A menção a `2x 200 Ohm` no Projeto 5 é erro de redação do manual. Confirma a padronização de R4/R5 em 220 Ω já aplicada no `diagram.json` do Wokwi.

**A4 — Relé identificado como HL-52S V1.0.** A Packing List diz apenas "5V 2-Channel Relay Module". O **diagrama de pinagem do manual** (p. 94–96) mostra a serigrafia `HL-52S V1.0` / `2 relay module` — ver §4.6. Ressalva: trata-se da ilustração do manual, não de uma fotografia da unidade em posse; a correspondência entre a ilustração e a placa física ainda merece conferência visual rápida na bancada.

**A5 — Protoboard de 830 pontos.** Confirma o MB-102 de tamanho completo já adotado no `diagram.json` do Wokwi.

**A6 — LEDs discretos são coloridos, não genéricos.** 15 LEDs por kit (5 vermelhos, 5 amarelos, 5 verdes). Onde o manual diz "5mm LED", qualquer uma das três cores serve. Não há LED azul discreto — só dentro do RGB.

**A7 — Cabos M-M são o recurso mais escasso.** 10 por kit (20 no total) para montagem em protoboard. Uma bancada com relé + LED RGB + botão + alimentação consome boa parte disso. Os cabos F-M e F-F servem para módulos com pinos fêmea (relé, DHT11, PIR).

---

## 3. Placa ESP32 DEVKIT V1 (DOIT) — especificações

Todos os dados desta seção são **fato documental** (manual, p. 5–7).

| Característica | Valor |
|---|---|
| Núcleos | 2 (dual core) — Tensilica Xtensa LX6 32 bits |
| Clock | até 240 MHz |
| Wi-Fi | 2,4 GHz, até 150 Mbit/s |
| Bluetooth | BLE + Bluetooth clássico |
| RAM | 512 KB (SRAM 520 KB no chip; 448 KB ROM) |
| Pinos | 30 |
| Ponte USB↔UART | **CP2102** |
| Botões | RESET (rotulado EN) e BOOT |
| LEDs on-board | Azul no **GPIO 2**; vermelho indica alimentação |
| Interface | micro-USB (alimentação + gravação) |
| Corrente máx. por GPIO | **40 mA** (absoluto, conforme datasheet ESP32) |
| Sensor extra | Sensor Hall integrado |

### 3.1 Restrições de GPIO — crítico para o Cafey

**Nunca usar (flash SPI interna do ESP-WROOM-32):** GPIO 6, 7, 8, 9, 10, 11.

**Somente entrada (sem pull-up/pull-down internos):** GPIO 34, 35, 36, 39.

**Strapping pins** — estado no boot afeta a inicialização:

| GPIO | Requisito no boot |
|---|---|
| 0 | LOW para entrar em modo boot; emite PWM no boot; tem pull-up |
| 2 | Flutuante ou LOW para entrar em modo gravação |
| 4 | Strapping pin |
| 5 | HIGH durante o boot; emite PWM no boot |
| 12 | **LOW durante o boot** — o boot falha se estiver em HIGH |
| 15 | HIGH durante o boot; emite PWM no boot |

**Pinos que vão a HIGH ou emitem PWM no boot/reset:** GPIO 1, 3, 5, 6–11, 14, 15.

> **Implicação direta para o módulo de relé**: o pino de comando do relé **não deve** ser alocado em nenhum GPIO desta lista. Com o HL-52S sendo *active-low*, um pino que sobe para HIGH no boot é seguro (relé desenergizado); já um GPIO que emite PWM no boot causaria chaveamento espúrio. Combinar com a decisão de firmware `kRelayIdleLevel`.

**Livres e recomendados (OK como entrada e saída, sem efeito no boot):** GPIO 13, 16, 17, 18, 19, 21, 22, 23, 25, 26, 27, 32, 33.

### 3.2 Periféricos por pino

- **ADC**: 18 canais de 12 bits (0–4095 ↔ 0–3,3 V). Na DEVKIT V1 de 30 pinos, **15 disponíveis**.
  - ADC1: GPIO 32, 33, 34, 35, 36, 37, 38, 39
  - ADC2: GPIO 0, 2, 4, 12, 13, 14, 15, 25, 26, 27
  - **Restrição: ADC2 não funciona com Wi-Fi ativo.** Como o Cafey usa Wi-Fi permanentemente, qualquer leitura analógica futura deve ficar em ADC1.
  - Resposta **não linear** nos extremos (~0–0,1 V e ~3,2–3,3 V).
- **DAC** (8 bits): GPIO 25, 26.
- **I²C** (padrão Arduino): SDA = GPIO 21, SCL = GPIO 22. Qualquer pino pode ser remapeado via `Wire.begin(SDA, SCL)`.
- **SPI**: VSPI → MOSI 23, MISO 19, CLK 18, CS 5. HSPI → MOSI 13, MISO 12, CLK 14, CS 15.
- **PWM**: 16 canais independentes; qualquer pino de saída serve (GPIO 34–39 não).
- **Touch capacitivo** (10 canais): T0=4, T1=0, T2=2, T3=15, T4=13, T5=12, T6=14, T7=27, T8=33, T9=32.
- **RTC GPIO** (wake-up de deep sleep): 0, 2, 4, 12, 13, 14, 15, 25, 26, 27, 32, 33, 34, 35, 36, 39.
- **Interrupções**: todos os GPIOs.
- **EN**: enable do regulador 3,3 V, com pull-up. Ligar ao GND desliga o regulador (reset por botão externo).

---

## 4. Módulo de relé (Projeto 7) — leitura integral

Seção mais relevante do manual para o Cafey. Conteúdo é **fato documental** (p. 94–99).

### 4.1 Lado de alta tensão

Dois conectores de 3 bornes cada:

- **COM** — corrente a ser controlada (rede elétrica).
- **NC (normalmente fechado)** — COM e NC conectados por padrão; corrente flui até o ESP32 comandar a abertura.
- **NO (normalmente aberto)** — sem conexão por padrão; corrente flui apenas quando comandado.

### 4.2 Lado de baixa tensão

Dois conjuntos de pinos:

1. **VCC, GND, IN1, IN2** — alimentação lógica e comando dos dois canais.
2. **GND, VCC, JD-VCC** — alimentação do eletroímã.

### 4.3 Polaridade de acionamento

O manual afirma: *o relé é acionado quando a entrada cai abaixo de aproximadamente 2 V* — ou seja, **acionamento por nível baixo (active-low)**, coerente com a identificação do HL-52S. Tabela do manual:

| Configuração | Sinal HIGH | Sinal LOW |
|---|---|---|
| NC (normalmente fechado) | corrente flui | corrente não flui |
| NO (normalmente aberto) | corrente não flui | corrente flui |

> **Verificado em bancada (2026-09-08, teste §4.6.3): acionamento *active-low* confirmado — IN em nível LOW energiza o relé.** A afirmação deixa de ser inferência a partir do manual e passa a ser resultado experimental. Consequências imediatas:
>
> - **Esquemático KiCad**: a polaridade do net `RELE_IN` ainda está desenhada como *active-high*. Deve ser corrigida para *active-low*, eliminando a última divergência entre o esquemático e a `spec-cafeteira-conectada.md`.
> - **Firmware**: `kRelayActiveLevel = 0`, `kRelayIdleLevel = 1`; `init()` deve escrever o nível de repouso **HIGH** antes de configurar o pino como saída.
> - **Configuração de contatos**: usar **NO**, mantendo a cafeteira desligada por padrão.

### 4.4 Jumper JD-VCC e isolação — ponto já registrado na spec

O manual é explícito:

- **Com o jumper VCC↔JD-VCC instalado**: o eletroímã é alimentado diretamente pelo pino de alimentação do ESP32, e *"o módulo de relé e os circuitos do ESP32 **não estão** fisicamente isolados entre si"*.
- **Sem o jumper**, alimentando JD-VCC por fonte independente: *"essa configuração isola fisicamente os relés do ESP32 através do optoacoplador embutido no módulo"*.

> Esta é a **fonte primária** que sustenta a correção pendente nos §4.5 e §9 da `spec-cafeteira-conectada.md`: com o jumper no lugar, o optoacoplador **não** provê isolação galvânica. A isolação restante é a rigidez dielétrica dos contatos do relé.

### 4.6 Pinagem física — HL-52S V1.0

O diagrama de pinagem do manual (p. 94–96) identifica a placa pela serigrafia: **`HL-52S V1.0`** na borda superior e **`2 relay module`** na inferior.

**Lado de alta tensão** — dois blocos de bornes de 3 vias, um por canal. Ordem em cada bloco, de cima para baixo:

| Posição | Borne |
|---|---|
| 1 | **NO** (normalmente aberto) |
| 2 | **COM** (comum) |
| 3 | **NC** (normalmente fechado) |

**Header de controle (4 vias)** — serigrafia `GndIn1In2Vcc`:

| Pino | Função |
|---|---|
| 1 | GND |
| 2 | **IN1** — canal inferior |
| 3 | **IN2** — canal superior |
| 4 | VCC (lógica, 5 V) |

**Header de alimentação da bobina (3 vias)** — serigrafia `JDVccVccGnd`:

| Pino | Função |
|---|---|
| 1 | **JD-VCC** — alimentação do eletroímã |
| 2 | VCC |
| 3 | GND |

O **jumper de fábrica ocupa as posições JD-VCC e VCC**, curto-circuitando as duas. É esse jumper que anula a função do optoacoplador (ver §4.4).

> **Nota sobre isolação no Cafey (inferência):** remover o jumper só produz isolação se JD-VCC receber uma fonte **independente** do rail que alimenta o ESP32. Na arquitetura atual, o HLK-PM01 alimenta ambos a partir do mesmo secundário — remover o jumper, nessa topologia, não cria isolação galvânica real. Isso é consistente com a redação corrigida da spec: a isolação efetiva vem da rigidez dielétrica dos contatos do relé.

---

### 4.5 Aviso de segurança do manual

O manual **substitui a carga de rede por LEDs de 5 mm** no experimento do Projeto 7, e recomenda desconectar tudo da rede durante a programação e a montagem. O Cafey vai além do escopo do manual ao comutar 127 V reais — o que reforça a necessidade da validação elétrica interna já prevista.

---

## 5. Toolchain documentada no manual

| Item | Valor |
|---|---|
| IDE | Arduino IDE (manual recomenda a linha **1.8.x** legada, por causa do plugin SPIFFS Uploader, ainda sem suporte na v2) |
| URL de Boards Manager | `https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json` |
| Pacote | "ESP32 by Espressif Systems" |
| Placa a selecionar | **ESP32 Dev Module** (ou *DOIT ESP32 DEVKIT V1*) |
| Driver USB | **CP210x USB to UART Bridge VCP** — obrigatório se a porta COM não aparecer |
| Baud rate do Serial Monitor | **115200** em todos os projetos |
| Sketch de teste | `File > Examples > WiFi (ESP32) > WiFiScan` |

> Observação: a recomendação de usar a Arduino IDE 1.8.x reflete o estado da ferramenta na data do manual (2023). Não confirmada contra fonte oficial atual — tratar como provisória. O Cafey usa ESP-IDF/PlatformIO, então esta seção serve apenas para testes rápidos de bancada com os componentes do kit.

### 5.1 Procedimento de recuperação de gravação

Se aparecer `A fatal error occurred: Failed to connect to ESP32: Timed out... Connecting...`:

1. Manter **BOOT** pressionado;
2. Clicar em **Upload** na IDE;
3. Soltar **BOOT** ao ver a mensagem `Connecting....`;
4. Após `Done uploading`, pressionar **EN** para reiniciar.

### 5.2 Bibliotecas exigidas pelos projetos

| Biblioteca | Projetos | Instalação indicada pelo manual |
|---|---|---|
| `WiFi.h` | 5, 6, 7, 8, 9 | Nativa do core ESP32 |
| `ESPAsyncWebServer` | 7, 8, 9 | ZIP manual (`Sketch > Include Library > Add .ZIP`) |
| `AsyncTCP` | 7, 8, 9 | ZIP manual — dependência da anterior |
| `DHT sensor library` | 9 | ZIP manual |
| `Adafruit Unified Sensor` | 9 | ZIP manual — dependência da anterior |
| `Adafruit_SSD1306` | 10 | Library Manager |
| `Adafruit_GFX` | 10 | Library Manager |

---

## 6. Mapa de pinos usado pelos projetos do manual

Útil como referência de bancada; **não** define a pinagem do Cafey.

| Projeto | Componente | GPIO |
|---|---|---|
| 1 | Push-button | 4 |
| 1 | LED | 5 |
| 2 | Potenciômetro (analógico) | 4 — ADC2_CH0 |
| 3 | LED (PWM) | 4 |
| 5 | LED 1 / LED 2 | 26 / 27 |
| 10 | OLED SDA / SCL | 21 / 22 (Vin em **3,3 V**) |

> Alerta: o Proj. 2 usa GPIO 4 (ADC2) para leitura analógica — combinação que **falha com Wi-Fi ativo**, contrariando a própria nota do manual na p. 14. Divergência interna do documento.

---

## 7. Divergências identificadas no manual

Registradas para não serem tratadas como verdade em decisões de projeto:

| # | Divergência | Páginas | Leitura adotada |
|---|---|---|---|
| D1 | Placa descrita como "30 pinos" na introdução e como "DOIT board with 36 pins" no Proj. 5 | 5 vs. 66 | **30 pinos** — corresponde à placa física |
| D2 | Resistor de 200 Ω no Proj. 5 vs. 220 Ω em todos os demais | 66 vs. 35/53/85/105 | **Resolvida** — o kit só contém 220 Ω (Packing List). Erro de redação do manual |
| D3 | Proj. 2 lê ADC2 (GPIO 4) sem ressalva, apesar do alerta "ADC2 não funciona com Wi-Fi" | 46 vs. 14 | Alerta prevalece; usar ADC1 quando houver Wi-Fi |
| D4 | Projetos 7 (relé) e 10 (OLED) não têm seção *Parts Required* | 94, 127 | **Resolvida** — ambos constam na Packing List |
| D5 | Projetos 1 e 8 exigem resistor de **10 kΩ**, que o kit não fornece | 35, 105 | Não executáveis como descritos. Usar pull-up interno do ESP32 ou adquirir o resistor (ver §2.1 A1) |
| D6 | Módulos IR de obstáculo e LDR constam na Packing List mas não são usados em nenhum dos 10 projetos | p. 1 | Componentes órfãos; sem documentação de uso no manual |

---

## 8. Aplicabilidade ao projeto Cafey

### 8.1 O que os 2 kits cobrem sem compra adicional

- **Protótipo de bancada do módulo de relé** (Seção 4 do artigo EnGeTec): ESP32 + módulo relé 5 V 2 canais + LED indicador + protoboard 830 pontos + cabos DuPont. Suficiente para o teste §4.6.3 (confirmação da polaridade *active-low*).
- **LED RGB de status**: 2 unidades cátodo comum por kit, com 30 resistores de 220 Ω disponíveis — cobre com folga os nets `LED_R/G/B`.
- **Botão físico local**: 6 chaves tácteis por kit para o net `BOTAO`. **Sem o resistor de pull-up** — ver §2.1 A1.
- **Bancada redundante**: o segundo kit permite manter uma montagem de referência íntegra enquanto a primeira é modificada, e é reserva imediata caso um ESP32 seja danificado nos testes com rede elétrica.
- **Instrumentação opcional**: DHT11 disponível se o projeto vier a monitorar temperatura ambiente ou da base da cafeteira — não previsto na arquitetura atual (conclusão de preparo é por temporizador, sem sensores).

### 8.2 O que os kits **não** cobrem

- **Resistor de 10 kΩ** — único item de baixo custo faltante para replicar os projetos do manual (§2.1 A1).
- Fonte AC-DC **HLK-PM01** (comutação da fase de rede).
- Bornes/conectores para 127 V, prensa-cabo PG9, cabo com plugue NBR 14136.
- Gabinete em aço inox, insertos M3, chapa-base.
- Cafeteira Britânia CP30 (carga de teste).

### 8.3 Pendências abertas

- [x] ~~Conferir a Packing List física e substituir a §2 por quantidades de fato~~ — concluído em 2026-09-08.
- [x] ~~Verificar se o kit inclui cabo micro-USB~~ — confirmado, 1 por kit.
- [ ] **Decidir o pull-up do net `BOTAO`**: interno do ESP32 (sem compra) ou resistor externo de 10 kΩ (compra). Ver §2.1 A1.
- [x] ~~Confirmar o modelo do módulo de relé~~ — **HL-52S V1.0**, conforme diagrama do manual (§4.6). Resta apenas conferir visualmente se a placa física traz a mesma serigrafia.
- [x] ~~Executar teste §4.6.3 de polaridade do relé~~ — concluído em 2026-09-08: **active-low confirmado**.
- [ ] **Corrigir a polaridade do net `RELE_IN` no esquemático KiCad** para *active-low* (era a última divergência aberta entre esquemático e spec).
- [ ] **Corrigir `relay.cpp`**: introduzir `kRelayActiveLevel`/`kRelayIdleLevel` e fazer `init()` escrever o nível de repouso antes de configurar o pino como saída.
- [ ] **Testar o comportamento do pino IN em alta impedância**: entre a energização da placa e a execução do `init()`, o GPIO do ESP32 fica como entrada flutuante. *Hipótese a verificar*: no HL-52S o IN é o cátodo do LED do optoacoplador, de modo que flutuante ⇒ sem corrente ⇒ relé desenergizado. Teste: alimentar o módulo com o IN desconectado e observar se o relé permanece aberto. Se não permanecer, é necessário um pull-up externo para VCC no IN.
- [ ] Verificar em qual GPIO o net `RELE_IN` está alocado no esquemático KiCad — evitar GPIO 1, 3, 5, 14, 15 (PWM/HIGH no boot; ver §3.1).

---

## 9. Procedência

| Campo | Valor |
|---|---|
| Documento-fonte | `ESP32_Basic_Starter_Kit_Tutorial.pdf`, 137 páginas |
| Versão do manual | V2.0.23.10.11 |
| Método de extração | Extração da camada de texto do PDF |
| Cobertura | Páginas 2–137 via camada de texto |
| Packing List (p. 1) | Sem camada de texto no PDF; transcrita de captura da página impressa |
| Data da extração | 2026-09-08 |
| Status | **Completo** para o conteúdo do manual. Pendências residuais em §8.3 dependem de inspeção do hardware físico, não do documento |