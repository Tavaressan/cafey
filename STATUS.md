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

- **Pino 16 do ESP32_30Pin:** corrigido para `power_out` tanto na biblioteca local (`esp32_30pin.kicad_sym`) quanto no esquemático incorporado, e o `PWR_FLAG` artificial da rede `+3V3` foi removido (PR #237, Issues #20 e #131). A rede `+3V3` é agora dirigida nativamente pelo regulador de bordo do ESP32 DevKit V1.

## Esquemático — referências (mudaram após reanotação)

| Ref | Componente |
|---|---|
| P1 | Cabo macho NBR 14136 (`Connector:Conn_Plug_3P_Protected`) |
| J1 | Tomada fêmea NBR 14136 (`Connector:Conn_Receptacle_3P_Protected`) |
| J3 | Borne de aterramento DIN verde-amarelo — ponto de junção do PE (passa-através rede→tomada), no nó `Earth_Protective`. Gabinete plástico FDM, sem massa metálica a aterrar (era "terra da chapa de aço" na versão em chapa) |
| U1 | ESP32 DevKit V1 |
| U2 | HLK-PM01 (`Converter_ACDC:HLK-PM01`) |
| K3 | Módulo relé, gatilho baixo, 2 canais (1 em uso) (`relay_module:Relay_Module_1CH`, símbolo único do projeto — representa o canal usado) |
| F1 | Fusível 10 A T / 250 V retardado |
| R1 | Resistor 10 kΩ pull-up de K3.IN para +3V3 |
| R2 | Resistor 10 kΩ pull-up de SW1 para +3V3 |
| R3/R4/R5 | Resistores dos anodos do LED RGB (220 Ω / 100 Ω / 100 Ω) |
| C1/C2/C3 | Capacitores de desacoplamento e debouncing (C1 470 µF, C2/C3 100 nF) |

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
- Net `+3V3` alimentado nativamente por U1.16 (`power_out`); `+5V` e `GND` dirigidos
  pelas saídas do HLK-PM01 (U2).
- Pull-up R1 desenhado canonicamente no topo (`+3V3` com rotação 0 apontando para cima)
  e `RELE_IN` roteado pela perna inferior até K3.IN (PR #237).
- Anotação feita; ERC rodado (0 erros, 22 avisos esperados e documentados).
- Netlist conferida nó a nó com `kicad-cli export netlist`.
- PDF exportado via `kicad-cli sch export pdf`.

## Esquemático — exclusões de ERC documentadas (HW-09 / #11)

- [x] **21 avisos de "pino não conectado":** GPIOs livres e pinos auxiliares do ESP32 (U1: EN, GPIO1, 2, 3, 4, 5, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 32, 33, 34, 35, 36, 39) — intencionais e esperados (apenas GPIO26, GPIO27, GPIO25/33/32 do LED e alimentação estão em uso).
- [x] **1 aviso `pin_to_pin`:** U1.GND (bidirecional) conectado a U2.-Vout (saída de potência) — inerente ao conversor HLK-PM01 alimentar e referenciar o plano de terra DC do ESP32.

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
08/09/2026 e atualizações recentes). Fusível fixado em **10 A T** (não 8 A): fica no limite do contato
do relé, protege a fiação do módulo e o cabo; menos abertura espúria que 8 A
sobre 6,3 A.

## Revisão e fechamento de pendências de hardware (15/10/2026)

- [x] **HW-19 / #130:** Regeneração completa da geometria FreeCAD FDM (`build_peca1_3d.py`, `build_peca2_3d.py`, `build_arranjo_3d.py`), propagando `furo_botao = 16`, relé 2 canais LAFVIN (50×39×19 mm), eliminação da janela RF no FDM (PETG transparente a 2.4 GHz) e validação matemática de 100% dos checks mecânicos (`check_peca1_3d`, `check_peca2_3d`, `check_montagem_3d`, `check_arranjo_3d`). STEP versionado atualizado.
- [x] **HW-18 / #20:** Símbolo `ESP32_30Pin` corrigido com pino 16 como `power_out`, permitindo a remoção do `PWR_FLAG` (#FLG02) da rede `+3V3`.
- [x] **HW-20 / #131:** Ajuste estético e elétrico do símbolo `#PWR08` (+3V3 do pull-up de R1) com rotação 0 para cima e roteamento limpo de `RELE_IN`.
- [x] **HW-07 / #9:** Confirmado F1 10 A T / 250 V retardado no esquemático e na lista de compras. Issue fechada.
- [x] **HW-12 / #14:** Separação física rede / baixa tensão com divisória de policarbonato 3 mm e folgas >= 10 mm modelada e validada no 3D. Issue fechada.
- [x] **HW-13 / #15:** Posição da antena ESP32 rente à parede frontal de PETG sem janela RF. Issue fechada.
- [x] **HW-14 / #16:** PE passa-através em borne DIN J3 / conector WAGO 221 para gabinete plástico FDM. Issue fechada.
- [x] **HW-15 / #17:** Superada pelo pivô arquitetural de carcaça impressa em 3D (FDM). Issue fechada.
- [x] **HW-11 / #13:** Planificação da chapa entregue em DXF analítico (`flatten_peca1.py`). Issue fechada.
- [x] **HW-10 / #12:** Dimensões físicas e modelos visuais STEP/IGES consolidados no arranjo 3D. Issue fechada.
- [ ] Conferir na bancada se o módulo de relé físico realmente liga com IN=LOW.
- [ ] Verificar se o plugue da CP30 tem o 3º pino (terra) populado.

## Frontend Multiplataforma (Compose Multiplatform / KMP — 15/10/2026)

Módulo `apps` 100% estabilizado, com 93 testes unitários e de UI passando em `:shared:desktopTest`:

- [x] **Issue #198 (PR #239):** Invalidação síncrona do cache do Bearer token do Ktor (`clearToken()`) e limpeza do storage no logout, eliminando 401s após troca de usuário.
- [x] **Issues #206 e #220 (PR #240):** Correção de scroll em telas compactas, tratamento diferenciado de conexão em Cuidados, botão de retry e desacoplamento do POST de baixa de cuidados da recarga de status.
- [x] **Issue #201 (PR #241):** Unificação estrutural de `ScheduleForm` com preservação de foco e teclado virtual (IME) entre redimensionamento de janela (cruzamento do breakpoint de 1024dp).
- [x] **Issue #178 (PR #242):** Identidade visual e logotipo da marca integrados em todas as plataformas (Android mipmaps/adaptive icons, Desktop icon de janela, Web favicon e composables `CafeyLogo`/`CafeyWordmark` em `LoginScreen` e `CafeySideNav`).
- [x] **Issue #186:** Higienização de branches remotas órfãs com bypass admin obsoleto.
- [x] **Issue #184 (PR #243):** Modo Demo completo em tempo de execução via `DemoMockEngine` do Ktor, fornecendo dados mockados realistas para apresentação acadêmica e testes de banca sem necessidade de backend ou hardware ativo.
- [x] **Issue #199 (PR #244):** Throttling inteligente de 60s em `HomeViewModel.refresh()` para consulta de métricas de consumo (`/estatisticas`), mantendo o polling de estado de 5s leve e evitando rajadas no servidor.


## Marcos

| Data | Critério |
|---|---|
| 04/09 | Relé aciona carga por comando do ESP32; backend autentica ponta a ponta |
| 20/09 | Submissão do artigo EnGeTec |
| 25/09 | Comando do mobile chega à base via AWS IoT |
| 02/10 | Montagem elétrica validada por profissional |
| 09/10 | Agendamento dispara offline; histórico com eventos reais |
| 16/10 | Projeto pronto para a banca |
