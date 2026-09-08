# Estado atual

Data: 31/08/2026. Fase 1 do cronograma (24/08 a 04/09).

Esquemático revisado: netlist conferida nó a nó com `kicad-cli`, ERC limpo
(salvo avisos esperados de GPIO livre e duas exclusões documentadas). Início da
prototipação embarcada no Wokwi — ver `firmware/`.

> **Atualização 08/09/2026:** o esquemático recebeu correções (relé active-low,
> R1 pull-up para +3V3, F1 10 A T). **ERC re-rodado com `kicad-cli` 10.0.5:
> 0 erros, 22 avisos** (os mesmos 21 GPIO livres + 1 `pin_to_pin` U1.GND↔U2.-Vout
> já documentados). Netlist reconferida: `RELE_IN` = {GPIO26, K3.IN, R1};
> `+3V3` = {U1.16, R2, R1}; `L_FONTE` = {F1.2, K3.COM, U2.L}. PDF regenerado.
> Ver "Revisão de compras e correções (08/09/2026)".

## Decisões fechadas

| Item | Decisão |
|---|---|
| Cafeteira | Britânia CP30 Inox, 800 W, 127 V, chave mecânica KCD1-106 (~6,3 A) |
| Alimentação 5 V | Módulo AC-DC HLK-PM01 embutido (5 V, 600 mA, isolado); prototipagem por USB |
| Módulo de relé | Módulo de 2 canais em estoque (LAFVIN), **1 canal em uso**; bobina 5 V, optoacoplador, ≥10 A, **gatilho de nível BAIXO** (HL-52S: LOW = liga) — ver "Revisão de compras e correções (08/09/2026)" |
| Isolamento | Fonte única, jumper JD-VCC mantido, ruído tratado por desacoplamento |
| LED | RGB discreto de cátodo comum (símbolo `Device:LED_RGBK`) |
| Proteção | Fusível **F1 = 10 A / 250 V retardado (T)** na entrada, em série com o caminho da carga (`L_REDE → F1 → relé COM → NO → tomada`); protege toda a fiação do módulo, incluindo a carga. Dimensionado entre a cafeteira (~6,3 A) e o contato do relé (10 A). Decisão de 08/09: manter a filosofia do esquemático (fusível protege a carga), **não** rebaixar para um fusível só do ramo do HLK-PM01 |
| MCU | DOIT ESP32 DevKit V1, 30 pinos |
| Esquemático | Folha única A4, KiCad 10.0.5 |

Duas decisões acima divergem da especificação e o texto dela ainda não foi
atualizado:

1. A §4.4 dava a alimentação como pendente. Está resolvida: HLK-PM01. O
   argumento decisivo foi que a tomada fêmea da base é chaveada pelo relé, então
   um carregador externo exigiria uma segunda tomada de parede.
2. A §4.5 e a §9 afirmam "isolamento galvânico por optoacoplador separando o
   ESP32 do lado da carga". Com o jumper JD-VCC de fábrica, isso é falso: a
   bobina compartilha rail e GND com o ESP32, e o optoacoplador não isola nada.
   A separação entre lógica e rede é dada pela rigidez dielétrica do relé. O
   texto precisa ser corrigido antes da banca.

## Pinagem definida

| Função | GPIO | Pino do símbolo | Componentes associados |
|---|---|---|---|
| Alimentação | VIN | 1 | +5V |
| Terra | GND | 2 | GND |
| Referência 3,3 V | 3.3v | 16 | +3V3 |
| Sinal do relé | GPIO26 | 7 | R1 10 kΩ **pull-up para +3V3** (módulo active-low) |
| Botão | GPIO27 | 6 | R2 10 kΩ pull-up, C3 100 nF |
| LED vermelho | GPIO21 | 26 | R3 220 Ω |
| LED verde | GPIO22 | 29 | R4 100 Ω |
| LED azul | GPIO23 | 30 | R5 100 Ω |

R1 é pull-up para +3V3 porque o módulo é active-low (LOW = relé ligado):
com o GPIO em alta impedância no boot, R1 mantém a entrada IN em nível ALTO
= relé desligado. Repouso e falha passam a ser o mesmo estado seguro. O
firmware reforça isso forçando o pino em ALTO antes de configurar o GPIO
(`firmware/drivers/relay.cpp`).

GPIO12 foi evitado (nível alto no boot impede a partida); GPIO2, 5 e 15 foram
evitados (strapping); GPIO34, 35, 36 e 39 são somente entrada.

O símbolo `ESP32_30Pin` declara o pino 16 (3V3) como entrada de energia; na
placa real o 3V3 é saída do regulador de bordo. Por isso a rede `+3V3` leva um
`PWR_FLAG` — curativo de ERC, sem efeito físico. Corrigir no símbolo algum dia.

## Esquemático — referências (mudaram após reanotação)

| Ref | Componente |
|---|---|
| P1 | Cabo macho NBR 14136 (`Connector:Conn_Plug_3P_Protected`) |
| J1 | Tomada fêmea NBR 14136 (`Connector:Conn_Receptacle_3P_Protected`) |
| J3 | Borne de aterramento DIN verde-amarelo — ponto de junção do PE (passa-através rede→tomada), no nó `Earth_Protective`. Gabinete plástico FDM, sem massa metálica a aterrar (era "terra da chapa de aço" na versão em chapa) |
| U1 | ESP32 DevKit V1 |
| U2 | HLK-PM01 (`Converter_ACDC:HLK-PM01`) |
| K3 | Módulo relé, gatilho baixo, 2 canais (1 em uso) (`relay_module:Relay_Module_1CH`, símbolo único do projeto — representa o canal usado) |
| F1 | Fusível | C1/C2/C3 | Capacitores (C3 = antigo C4, buraco fechado) |

## Esquemático — o que está pronto

- Bloco de potência: P1 (cabo macho) → F1 → COM/NO de K3 → J1 (tomada fêmea).
  Neutro e terra passam diretos.
- J3 (borne de aterramento DIN, ex-"terminal olhal da chapa") e o símbolo
  `Earth_Protective` no nó de terra; a rede se chama `Earth_Protective` e liga
  P1.PE, J1.PE e J3.
- PE e GND são redes separadas, sem ponto de encontro. A saída do HLK-PM01 é
  isolada e seu negativo não vai ao terra de proteção.
- Derivação da fase para a fonte entre F1 e o contato do relé, via rótulo
  `L_FONTE`. `L_REDE` = fase da rede antes do fusível; `L_SAIDA` = fase chaveada
  para a tomada.
- Todos os componentes com símbolo dedicado (HLK-PM01, relé, plugue/tomada
  protegidos) e campos de Valor preenchidos.
- `PWR_FLAG` em `+3V3`, `L_REDE` e `L_SAIDA` (o `+5V` e o `GND` são dirigidos
  pelos pinos de saída do HLK-PM01).
- Anotação feita; ERC rodado.
- Netlist conferida nó a nó com `kicad-cli export netlist`.
- Notas de texto no bloco de potência (tensão, corrente, aviso de validação).

## Esquemático — o que falta

- [ ] Atribuir footprints a todos os componentes (nenhum tem footprint ainda) —
      pré-requisito do PCB
- [ ] Dois avisos de ERC ficam como exclusão documentada: `power_out ↔ power_out`
      em `N` e em `Earth_Protective` (passagem direta plugue→tomada, inerente aos
      símbolos `*_3P_Protected`)
- [ ] Aviso `pin_to_pin` entre U1.GND (bidirecional) e U2.-Vout — inofensivo
- [ ] 21 avisos de "pino não conectado" nos GPIOs livres do U1 — esperados

## Pendências de bancada

Nenhuma delas é decisão — são verificações contra o componente físico.

- [ ] Conferir serigrafia do DevKit pino a pino contra o símbolo `ESP32_30pin`
- [ ] Conferir ordem VCC/GND/IN e NC/COM/NO na serigrafia do módulo de relé
      (varia por fabricante; trocar COM com NO inverte a lógica da cafeteira)
- [ ] Identificar as pernas físicas do LED RGB com multímetro em teste de
      diodo — a numeração do símbolo é lógica, não física
- [ ] Validação da montagem elétrica por profissional (marco de 02/10)

## Compras pendentes

Lista completa e revisada em `docs/lista-compras-cafeteira.md` (revisão de
08/09/2026). Fusível fixado em **10 A T** (não 8 A): fica no limite do contato
do relé, protege a fiação do módulo e o cabo; menos abertura espúria que 8 A
sobre 6,3 A. Itens `MEDIR` (E2 tomada, E10 prensa-cabo, M1 botão) compram
primeiro e alimentam o modelo mecânico.

## Revisão de compras e correções (08/09/2026)

Revisão técnica da lista de compras cruzada com esquemático, firmware e modelo
mecânico. Decisões tomadas nesta sessão e o que falta aplicar.

### Decisões

| Tema | Decisão |
|---|---|
| Filosofia do fusível | Manter o esquemático: F1 em série com a carga, protege todo o módulo. **Não** rebaixar para fusível só do ramo do HLK-PM01. |
| Valor de F1 | 10 A / 250 V **retardado (T)**. |
| Gatilho do relé | **Active-LOW** (firmware/Wokwi estavam certos; esquemático e STATUS estavam errados). R1 vira pull-up para +3V3. |
| Módulo de relé | É de **2 canais** (LAFVIN), usa 1. Atualizar o modelo mecânico para o volume real de 2 canais. |
| Botão de painel | **16 mm** metálico momentâneo NA. Atualizar `furo_botao` no modelo (era 12 mm). |
| Cabo de força (E1) | **3G1,0 mm²**, ⌀ externo ≤ 8 mm (entra no PG9 — não subir para 1,5 mm²). |
| Ponto de terra (PE) | **Borne de aterramento DIN verde-amarelo** (mantém o papel do J3). Gabinete plástico não tem massa a aterrar. |
| Bonding do botão/USB metálicos ao PE | **Em aberto** — decisão do profissional habilitado no marco de 02/10. |

### Correções já aplicadas nesta sessão

- `hardware/pi_dsm_26_2.kicad_sch`:
  - `K3` Value: "1 canal, gatilho alto" → "2 canais (1 em uso), gatilho baixo (HL-52S, LOW=liga)".
  - `F1` Value: "10 A / 250 V" → "10 A T / 250 V".
  - R1 pull-down → pull-up: símbolo `#PWR08` trocado de `power:GND` para `power:+3V3` (rotação 180) no pino inferior de R1.
  - `J3` Value: "Terra da chapa de aço — terminal olhal" → "Borne de aterramento DIN verde-amarelo…".
  - ERC re-rodado (0 erros / 22 avisos esperados); `pi_dsm_26_2.pdf` regenerado.
- `STATUS.md`: tabela de decisões, pinagem, referências, compras pendentes (este arquivo).
- `mechanical/params/parametros_3d.csv`: `furo_botao` 12 → 16 mm.
- `mechanical/params/componentes_3d.csv`: `modulo_rele` redimensionado para módulo de 2 canais.
- `mechanical/FUROS.md`: nota do `furo_botao` (12 → 16 mm) e do porta-fusível.
- `docs/lista-compras-cafeteira.md`: criado.

### Falta aplicar (não dá para fazer neste ambiente)

- [x] ERC re-rodado (`kicad-cli` 10.0.5): 0 erros, 22 avisos esperados. Netlist
      reconferida. PDF regenerado.
- [ ] Abrir no KiCad e conferir a **aparência** do símbolo `#PWR08` (trocado de
      `power:GND` para `power:+3V3`, rotação 180 por edição de texto) e reanotar
      as refs de power se necessário — cosmético, o ERC/netlist já estão corretos.
- [ ] Regenerar o modelo mecânico (`freecadcmd mechanical/scripts/build_peca1_3d.py`
      etc.) para propagar `furo_botao = 16` e o novo volume do relé; conferir
      `check_peca*.py`.
- [ ] Medir a placa de relé 2 canais LAFVIN real e ajustar o envelope
      `modulo_rele` com o valor medido (o valor atual é estimado).
- [ ] Reposicionar `modulo_rele` no compartimento de rede se o volume maior
      colidir com `hlk_pm01` ou `porta_fusivel`.
- [ ] Atualizar a §4.5 e a §9 da especificação (isolamento) — pendência antiga,
      ainda aberta.
- [ ] Conferir na bancada se o módulo de relé físico realmente liga com IN=LOW.
- [ ] Verificar se o plugue da CP30 tem o 3º pino (terra) populado.

## Marcos

| Data | Critério |
|---|---|
| 04/09 | Relé aciona carga por comando do ESP32; backend autentica ponta a ponta |
| 20/09 | Submissão do artigo EnGeTec |
| 25/09 | Comando do mobile chega à base via AWS IoT |
| 02/10 | Montagem elétrica validada por profissional |
| 09/10 | Agendamento dispara offline; histórico com eventos reais |
| 16/10 | Projeto pronto para a banca |
