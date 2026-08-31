# Estado atual

Data: 31/08/2026. Fase 1 do cronograma (24/08 a 04/09).

Esquemático revisado: netlist conferida nó a nó com `kicad-cli`, ERC limpo
(salvo avisos esperados de GPIO livre e duas exclusões documentadas). Início da
prototipação embarcada no Wokwi — ver `firmware/`.

## Decisões fechadas

| Item | Decisão |
|---|---|
| Cafeteira | Britânia CP30 Inox, 800 W, 127 V, chave mecânica KCD1-106 (~6,3 A) |
| Alimentação 5 V | Módulo AC-DC HLK-PM01 embutido (5 V, 600 mA, isolado); prototipagem por USB |
| Módulo de relé | 1 canal, bobina 5 V, optoacoplador, ≥10 A, gatilho de nível ALTO |
| Isolamento | Fonte única, jumper JD-VCC mantido, ruído tratado por desacoplamento |
| LED | RGB discreto de cátodo comum (símbolo `Device:LED_RGBK`) |
| Proteção | Fusível na entrada, antes do relé. Valor no esquemático: 10 A / 250 V — ver "Compras pendentes" (8 A dá margem entre a cafeteira ~6,3 A e o contato do relé 10 A) |
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
| Sinal do relé | GPIO26 | 7 | R1 10 kΩ pull-down para GND |
| Botão | GPIO27 | 6 | R2 10 kΩ pull-up, C3 100 nF |
| LED vermelho | GPIO21 | 26 | R3 220 Ω |
| LED verde | GPIO22 | 29 | R4 100 Ω |
| LED azul | GPIO23 | 30 | R5 100 Ω |

R1 é pull-down porque o módulo é de gatilho alto: repouso e falha passam a ser
o mesmo estado, o que impede acionamento espúrio do relé durante o boot,
quando o GPIO fica em alta impedância.

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
| J3 | Terra da chapa de aço — terminal olhal (`Conn_01x01`), no nó `Earth_Protective` |
| U1 | ESP32 DevKit V1 |
| U2 | HLK-PM01 (`Converter_ACDC:HLK-PM01`) |
| K3 | Módulo relé 1 canal (`relay_module:Relay_Module_1CH`, símbolo único do projeto) |
| F1 | Fusível | C1/C2/C3 | Capacitores (C3 = antigo C4, buraco fechado) |

## Esquemático — o que está pronto

- Bloco de potência: P1 (cabo macho) → F1 → COM/NO de K3 → J1 (tomada fêmea).
  Neutro e terra passam diretos.
- J3 (terminal olhal) e o símbolo `Earth_Protective` no nó de terra da chapa;
  a rede se chama `Earth_Protective` e liga P1.PE, J1.PE e J3.
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

Cafeteira, módulo de relé (gatilho alto ou selecionável), HLK-PM01.

Fusível: considerar 8 A em vez de 10 A — fica acima dos ~6,3 A da cafeteira e
abaixo do limite do contato do relé (10 A), protegendo também o contato. Se
comprar 8 A, atualizar o Valor de F1 no esquemático.

## Marcos

| Data | Critério |
|---|---|
| 04/09 | Relé aciona carga por comando do ESP32; backend autentica ponta a ponta |
| 20/09 | Submissão do artigo EnGeTec |
| 25/09 | Comando do mobile chega à base via AWS IoT |
| 02/10 | Montagem elétrica validada por profissional |
| 09/10 | Agendamento dispara offline; histórico com eventos reais |
| 16/10 | Projeto pronto para a banca |
