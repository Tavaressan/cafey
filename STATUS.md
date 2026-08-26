# Estado atual

Data: 26/08/2026. Fase 1 do cronograma (24/08 a 04/09).

## Decisões fechadas

| Item | Decisão |
|---|---|
| Cafeteira | Britânia CP30 Inox, 800 W, 127 V, chave mecânica KCD1-106 (~6,3 A) |
| Alimentação 5 V | Módulo AC-DC HLK-PM01 embutido (5 V, 600 mA, isolado); prototipagem por USB |
| Módulo de relé | 1 canal, bobina 5 V, optoacoplador, ≥10 A, gatilho de nível ALTO |
| Isolamento | Fonte única, jumper JD-VCC mantido, ruído tratado por desacoplamento |
| LED | RGB discreto de cátodo comum (símbolo `Device:LED_RGBK`) |
| Proteção | Fusível 10 A / 250 V na entrada, antes do relé |
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
| Botão | GPIO27 | 6 | R2 10 kΩ pull-up, C4 100 nF |
| LED vermelho | GPIO21 | 26 | R3 220 Ω |
| LED verde | GPIO22 | 29 | R4 100 Ω |
| LED azul | GPIO23 | 30 | R5 100 Ω |

R1 é pull-down porque o módulo é de gatilho alto: repouso e falha passam a ser
o mesmo estado, o que impede acionamento espúrio do relé durante o boot,
quando o GPIO fica em alta impedância.

GPIO12 foi evitado (nível alto no boot impede a partida); GPIO2, 5 e 15 foram
evitados (strapping); GPIO34, 35, 36 e 39 são somente entrada.

## Esquemático — o que está pronto

- Bloco de potência: J1 (cabo macho NBR 14136) → F1 → COM/NO de K2 → J2
  (tomada fêmea). Neutro e terra passam diretos.
- J3 como terminal olhal de aterramento da chapa de aço, no nó PE.
- PE e GND são redes separadas, sem ponto de encontro. A saída do HLK-PM01 é
  isolada e seu negativo não vai ao terra de proteção.
- Derivação da fase para a fonte entre F1 e o contato do relé, via rótulo
  `L_FONTE` — necessário para o ESP32 permanecer energizado com o relé aberto.
- PS1 (HLK-PM01), U1, K1 e K2 (módulo de relé em dois blocos, dentro de
  retângulo tracejado), SW1, D1, C1, C2, C4, R1 a R4 posicionados.
- PWR_FLAG em +5V, +3V3 e GND.
- Ligações por rótulos globais, não por fios longos.

## Esquemático — o que falta

Parei no passo 9 do roteiro (anotação e ERC).

- [ ] R5 (100 Ω, canal azul do LED) não foi colocado
- [ ] Campos de Valor vazios em todos os R, C, F1, J1 e J2 — aparecem como
      `R_US`, `C`, `Fuse` e nomes de biblioteca
- [ ] Referência `K1b1` inválida; renomear para `K2`
- [ ] Pino NC de K2 sem marca de "sem conexão"
- [ ] Rodar anotação
- [ ] Rodar ERC e resolver erros de alimentação
- [ ] Conferir que não há redes com nomes quase idênticos tratadas como
      distintas
- [ ] Nota de texto no bloco de potência (tensão, corrente, aviso de validação
      elétrica)
- [ ] Page Settings: data e revisão vazias
- [ ] Exportar netlist para conferência nó a nó contra a especificação

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

## Marcos

| Data | Critério |
|---|---|
| 04/09 | Relé aciona carga por comando do ESP32; backend autentica ponta a ponta |
| 20/09 | Submissão do artigo EnGeTec |
| 25/09 | Comando do mobile chega à base via AWS IoT |
| 02/10 | Montagem elétrica validada por profissional |
| 09/10 | Agendamento dispara offline; histórico com eventos reais |
| 16/10 | Projeto pronto para a banca |
