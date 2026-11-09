# Lista de compras — Cafeteira Conectada

Projeto: retrofit IoT da cafeteira Britânia CP30 (800 W, 127 V, ~6,3 A).
Revisão: 2026-09-08. Prazo de defesa: 2026-10-16.
**Revisão B: 2026-11-09** — componentes da montagem de 10/11/2026 (#304–#309, #311).

Esta é a versão revisada da lista, cruzada com o esquemático
(`hardware/pi_dsm_26_2.kicad_sch`), o firmware (`firmware/`) e o modelo
mecânico (`mechanical/`). As decisões de revisão estão registradas em
`STATUS.md`, seção "Revisão de compras e correções (08/09/2026)".

## Instruções para quem for comprar

1. Comprar **apenas** os itens da seção "Comprar". A seção "NÃO comprar" existe
   para evitar duplicidade — esses itens já estão em estoque.
2. Cada item traz `busca` (termo de pesquisa), `exigido` (especificação que não
   pode faltar) e `rejeitar` (variantes erradas comuns). Se nenhum resultado
   atender a `exigido`, **não substituir por aproximação** — reportar o item
   como não resolvido.
3. Preços e disponibilidade não foram verificados. Conferir no momento da compra.
4. Prioridade P1 antes de P2. Os itens marcados `MEDIR` devem chegar primeiro:
   as cotas deles alimentam o modelo mecânico, que está em análise.
5. Ao final, gerar um resumo com: itens no carrinho, itens não resolvidos, total.

---

## Revisão B (09/11/2026) — situação dos itens

A montagem de 10/11/2026 usa componentes que **substituem** parte desta lista.
O esquemático correspondente é a revisão B (`hardware/README.md`). Os itens
abaixo prevalecem sobre as tabelas de compra mais adiante, que ficam como
registro da revisão A.

### Componentes da revisão B

| ID | Item | Modelo | Situação | Issue |
|---|---|---|---|---|
| B1 | Relé de estado sólido (SSR1) | Fotek **SSR-40DA**: entrada 3–32 V DC, saída 24–380 V AC | Adquirido; autenticidade confirmada pelo responsável. Etiqueta, curva de redução e fuga **a registrar** | #305 |
| B2 | Dissipador aletado de alumínio + pasta térmica | — | Dimensões e furação **a medir**. Se não houver em mãos, o CAD usa envelope provisório | #305 |
| B3 | Fonte 5 V (PS1) | Apple **A1385**, 100–240 V AC → 5 V / 1 A, saída USB-A | Adquirida; modelo confirmado; testada pelo responsável (220 V → 5 V). Foto da etiqueta **a registrar** | #306 |
| B4 | Cabo USB-A → micro-USB | — | Comprimento, percurso e alívio de tração **a definir**. Precisa sair do DevKit sem forçar o conector da placa | #306, #309 |
| B5 | Disjuntor 10 A (CB1) | — | Adquirido. Fabricante, curva, polos, capacidade de interrupção e fixação (DIN ou painel) **a registrar** | #307 |
| B6 | Botão momentâneo com anel de LED RGB (SW1) | — | Adquirido. Nome comercial, polaridade do LED, resistor interno e furo de painel **a registrar** | #308 |
| B7 | Buzzer | — | Ativo ou passivo, tensão e corrente **a registrar** | #311 |

### Itens fora de uso na revisão B

| ID | Item | Motivo | Issue |
|---|---|---|---|
| E11 | HLK-PM01 | Substituído pela fonte Apple A1385 (B3) | #306 |
| E12 | Porta-fusível in-line F-103C | O fusível F1 foi substituído pelo disjuntor (B5) | #307 |
| E13 | Fusível 10 A T | Idem | #307 |
| M1 | Botão metálico 16 mm | Substituído pelo botão RGB (B6) | #308 |
| M2 | Aro para LED 5 mm | Sem LED discreto: o LED de estado é o anel do botão RGB | #308 |
| M8 | Cabo extensor micro-USB de painel | **Descartado:** a carcaça não terá abertura USB externa. A gravação é manual, retirando o ESP32 e usando o micro-USB da própria placa | #309 |
| — | Módulo de relé HL-52S (estoque) | Substituído pelo SSR (B1). **Não ligar ao GPIO26 com o firmware atual**, que é active-high e acionaria a carga no boot | #305 |
| — | LED RGB 5 mm do kit (D1) | Substituído pelo anel do botão RGB | #308 |

Itens que continuam, com ressalvas:

- **E15 (trilho DIN):** rever o comprimento se o disjuntor for de fixação DIN (#307).
- **M7 (JST-XH):** recontar as vias depois de registrar o botão RGB (#308) e o
  buzzer (#311). Os rabichos destacáveis (botão, anel, buzzer, entrada do SSR)
  mantêm o ESP32 removível sem solda (#309).
- **E3/E4 (WAGO):** a contagem de emendas da revisão B está abaixo, em
  "Contagem de conectores".

---

## Comprar — P1 · Rede elétrica 127 V

| ID | Item | Qtd | Busca | Exigido | Rejeitar |
|---|---|---|---|---|---|
| E1 | Cabo de força com plugue | 1 | `cabo de força tripolar 3G1,0mm 2P+T NBR 14136 1,5m` | 2P+T, 10 A, **condutor 1,0 mm²**, comprimento 1,2–2,0 m, **⌀ externo ≤ 8 mm** (tem de entrar no PG9 do E10) | Bitola 0,75 mm²; **1,5 mm² (⌀ externo grande demais para PG9)**; plugue sem terra; padrão americano/europeu |
| E2 | Tomada fêmea de painel `MEDIR` | 1 | `tomada femea painel 2P+T 10A NBR 14136` | **10 A** (pinos 4,0 mm), fixação em painel ou embutir | Versão 20 A (pinos 4,8 mm); tomada de parede sem furação frontal |
| E3 | Conector de emenda com alavanca | 4 | `conector emenda WAGO 221-412 2 vias` | 2 vias, até 4 mm², com alavanca — **confirmado:** [WAGO 221-412 (Casa do Eletricista)](https://www.casadoeletricistasc.com.br/con-p-emenda-c-alav-2-fios-4mm-wago/p/4134) | Modelos push-in sem alavanca (773/2273) |
| E4 | Conector de emenda com alavanca | 2 | `conector emenda WAGO 221-413 3 vias` | 3 vias, até 4 mm², com alavanca — **confirmado:** [WAGO 221-413 (Casa do Eletricista)](https://www.casadoeletricistasc.com.br/con-p-emenda-c-alav-3-fios-4mm-wago/p/4135) | Idem E3 |
| E5 | Cabo flexível preto | 1 m | `cabo flexivel 1,5mm 750V preto` | 1,5 mm², isolação 750 V | 300 V; cabo rígido |
| E6 | Cabo flexível azul | 1 m | `cabo flexivel 1,5mm 750V azul` | Idem E5 | Idem E5 |
| E7 | Cabo flexível verde-amarelo | 1 m | `cabo flexivel 1,5mm 750V verde amarelo` | Idem E5 | Verde liso (terra deve ser verde-amarelo) |
| E8 | Terminais tubulares pré-isolados | 20 | `terminal tubular ilhos pre-isolado 1,5mm` | Para 1,5 mm², pré-isolado | Terminal olhal, garfo ou pino chato |
| E9 | Termorretrátil | 1 m ⌀2,5 + 1 m ⌀4 | `tubo termorretratil 2,5mm 4mm kit` | Razão 2:1 | — |
| E10 | Prensa-cabo `MEDIR` | 1 | `prensa cabo PG9 nylon com porca` | PG9, faixa de aperto compatível com o ⌀ externo de E1 | Metálico (rosca em plástico impresso); PG7 ou PG11 sem conferir o cabo |
| E11 | Módulo AC-DC | 2 | `HLK-PM01 fonte AC-DC 5V 3W` | Saída 5 V, 600 mA, entrada 100–240 V. **1 em uso + 1 reserva** (não é arquitetura de duas fontes) | HLK-PM03 (3,3 V); HLK-PM12 (12 V); HLK-5M05 (formato diferente) |
| E12 | Porta-fusível **in-line** | 1 | `porta fusivel 5x20 in-line com rabicho` | Para fusível 5×20 mm, **corpo fechado, com rabicho, para fixação por abraçadeira** — **confirmado:** [F-103C (Eletrodex)](https://www.eletrodex.net/acessorios/passivos/fusiveis/porta-fusivel-meio-de-fio-f103c) | **Porta-fusível de painel** (exige furo que o modelo mecânico proíbe); de PCB; de engate rápido |
| E13 | Fusível de vidro | 5 | `fusivel vidro 5x20 10A retardado` | 5×20 mm, **10 A**, retardado (tipo T) | Ação rápida (F); ≤ 6,3 A (abre com a carga); acima de 10 A |
| E14 | Borne de aterramento DIN | 1 | `borne aterramento trilho DIN verde amarelo 4mm` | Verde-amarelo, para trilho DIN 35 mm, 4–6 mm² | Borne comum (não identificado como terra); tipo parafuso de chapa |
| E15 | Trilho DIN + travas | 1 | `trilho DIN 35mm + trava fim de trilho` | ~5 cm de trilho 35 mm + 2 travas de fim de trilho | — (alternativa: dispensar e projetar clipe no boss impresso — decisão do modelo mecânico) |

**Intenção de projeto de E12/E13 (revisada 08/09/2026; revisão A — na revisão B o
disjuntor B5 ocupa a mesma posição no circuito, #307):** F1 protege **todo o
módulo, incluindo a carga** — está em série no caminho
`L_REDE → F1 → relé COM → NO → tomada`. Dimensionado entre a cafeteira
(~6,3 A) e o contato do relé (10 A). O disjuntor da instalação continua sendo a
proteção primária de sobrecorrente; F1 limita o let-through numa falta interna
do módulo e protege o contato do relé.

**Intenção de projeto de E14/E15:** o gabinete é impresso em plástico (FDM) —
não há massa metálica a aterrar. O PE entra pelo cabo, passa pelo borne DIN e
sai para a tomada da cafeteira. O borne é um ponto de junção **identificável
como terra** para a inspeção elétrica, mais robusto contra puxão que um
conector de alavanca. Continuidade cabo→tomada é o item crítico de segurança.

## Comprar — P2 · Painel e montagem eletrônica

| ID | Item | Qtd | Busca | Exigido | Rejeitar |
|---|---|---|---|---|---|
| M1 | Botão momentâneo de painel `MEDIR` | 1 | `push button metalico 16mm momentaneo NA painel` | ⌀ furo **16 mm**, momentâneo (NA), com porca de fixação | Retentivo/trava (latching); versão com anel de LED; 12 mm ou 19 mm |
| M2 | Suporte/aro para LED | 1 | `suporte aro para LED 5mm painel` | Para LED 5 mm | Para 3 mm ou 10 mm |
| M3 | Placa ilhada perfurada | 2 | `placa ilhada perfurada 5x7cm fenolite` | Furos a 2,54 mm, ilhas individuais | Placa de fita/barramento contínuo; placa virgem sem furos |
| M4 | Barra de pinos fêmea | 2 | `barra de pinos femea 1x40 2,54mm` | Passo 2,54 mm, cortável | Passo 2,00 mm; barra empilhável alta |
| M5 | Barra de pinos macho | 1 | `barra de pinos macho 1x40 2,54mm` | Passo 2,54 mm | Passo 2,00 mm |
| M6 | Fio rígido para montagem | 1 kit | `fio rigido 22AWG kit cores jumper solda` | 22 AWG, rígido, ≥3 cores | Fio flexível fino sem estanhar |
| M7 | Conectores JST-XH (rabichos do LED e do botão) | 4 vias: 2 conjuntos; 2 vias: 2 conjuntos (1 + reserva cada) | `conector JST XH 4 vias cabo pre crimpado header 180 graus` / `... 2 vias ...` | Passo 2,5 mm; header macho PTH vertical (180°) + cabo pré-crimpado ≥ 10 cm (4 vias) e ≥ 15 cm (2 vias) | JST-PH (passo 2,0 mm); JST-SM; header SMD |
| M8 | Cabo extensor micro-USB de painel `MEDIR` | 1 | `cabo extensor micro usb painel macho femea com parafuso` | Micro-B macho → micro-B fêmea com flange de fixação por parafuso; 20–30 cm; dados (não só carga) — **escolhido:** [Mercado Livre MLB-1781256517, 30 cm, 5 pinos](https://produto.mercadolivre.com.br/MLB-1781256517-cabo-micro-usb-para-montagem-painel-macho-femea-30-cm-_JM) | Só carga; USB-C; sem flange de painel; versão de 50 cm do mesmo vendedor |

> **M8 descartado em 09/11/2026 (#309):** sem abertura USB externa na carcaça.
> O registro abaixo fica como histórico da escolha.

**Definição de M8 (02/11/2026):** comprar o cabo genérico do Mercado Livre
acima, o mais barato que atende a `exigido`: **R$ 28 + frete** (preço do
anúncio em 02/11/2026). Alternativas avaliadas e descartadas pelo preço: cabo
zdyCGTime de 30 cm na Amazon BR (R$ 66,05), SparkFun de 15 cm na Amazon BR
(R$ 79,62; curto demais) e o Adafruit 3258 original (US$ 4,95; o frete
internacional e o imposto superam o preço de uma unidade).

- **Dados:** o anúncio diz "micro usb 2.0 5pin" nos dois lados, sem dizer
  "dados". Perguntar ao vendedor antes de comprar. Ao receber, ligar o ESP32
  pelo cabo e confirmar que ele aparece como porta serial. Se não aparecer, o
  cabo é só de carga: devolver.
- **Parafusos:** o anúncio não diz que vêm. Usar 2 × M3 × 8 mm (A3). Com a
  flange por fora da parede, M3 × 12 mm passa da orelha e fica cerca de 4,6 mm
  para fora da caixa.
- **Medidas (`MEDIR`):** o anúncio não traz cotas. A descrição é a dos clones
  do Adafruit 3258, então a referência provável é corpo de 12 × 9,8 mm, flange
  de 25 × 9,8 × 5 mm e furos de Ø 3 mm a cerca de 17,3 mm entre centros. Medir
  a peça recebida antes de imprimir: o `recorte_usb` atual (16 × 9 mm, sem
  furos de fixação) não aceita esse conector (ver `mechanical/models/FONTES.md`).

## Verificar em estoque antes de comprar (Alfabra)

Comprar somente o que não existir:

| ID | Item | Qtd |
|---|---|---|
| A1 | Insertos roscados a quente, latão M3 × 5 mm | 20 |
| A2 | Ponteira M3 de inserto para ferro de solda | 1 |
| A3 | Parafuso M3 inox 8 mm | 10 |
| A4 | Parafuso M3 inox 10 mm | 10 |
| A5 | Parafuso M3 inox 12 mm | 10 |
| A6 | Pés de borracha adesivos | 4 |
| A7 | Retalho de chapa 1 mm (alumínio ou aço) para interposto térmico | 1 |
| A8 | Multímetro | 1 |

## NÃO comprar — já em estoque

- ESP32 DEVKIT V1 — kit LAFVIN + 1 sobressalente
- Módulo de relé **2 canais** 10 A — kit LAFVIN + 1 sobressalente (usa 1 canal;
  é **active-low** / gatilho de nível BAIXO — HL-52S). **Fora de uso na revisão
  B** (substituído pelo SSR, #305)
- LED RGB 5 mm, pushbutton de protoboard, resistores 220 Ω, protoboard
  MB-102, jumpers, cabo USB — kit LAFVIN
- Resistores 10 kΩ (R1, R2) — em estoque, fora do kit LAFVIN, que só traz
  220 Ω (confirmado em 08/10/2026; ver `docs_materiais/esp32_kit.md`, A1)
- Capacitores C1 470 µF / 16 V eletrolítico, C2 e C3 100 nF — Alfabra
  (confirmado em 08/10/2026)
- Filamento PETG e ASA — Alfabra
- Estanho para solda — Alfabra
- Alicate de crimpar terminal tubular — Alfabra
- Termômetro IR / termopar — empréstimo Alfabra

---

## Contagem de conectores (derivada do esquemático)

### Revisão B (09/11/2026)

Pontos físicos de cada nó de rede, pela netlist da revisão B. O tipo de emenda
de cada nó (WAGO, borne ou terminal do próprio componente) só será fixado no
diagrama de conexões fio a fio, depois de validar #305–#307 (#304). O esboço do
responsável usa um borne de 3 posições na entrada (L, PE, N) e outro na saída
para a tomada.

| Nó | Pontos físicos |
|---|---|
| `L_REDE` | cabo P1 (L) · entrada do disjuntor CB1 |
| `L_FONTE` | saída do CB1 · SSR terminal 1 · A1385 (L, direto no pino) |
| `L_SAIDA` | SSR terminal 2 · tomada J1 (L) |
| `N` | cabo P1 (N) · tomada J1 (N) · A1385 (N, direto no pino) |
| `PE` | cabo P1 (PE) · tomada J1 (PE) — borne DIN E14 |

### Revisão A (histórico)

Nós de rede e o que se junta em cada um:

| Nó | Pontos físicos | Conector |
|---|---|---|
| `L_REDE` | cabo P1 (L) · entrada do F1 | 1× WAGO 221-412 (2 vias) |
| `L_FONTE` | saída do F1 · relé COM · HLK-PM01 (L) | 1× WAGO 221-413 (3 vias) |
| `L_SAIDA` | relé NO · tomada J1 (L) | 1× WAGO 221-412 (2 vias) |
| `N` | cabo P1 (N) · tomada J1 (N) · HLK-PM01 (N) | 1× WAGO 221-413 (3 vias) |
| `PE` | cabo P1 (PE) · tomada J1 (PE) | borne DIN E14 (não usa WAGO) |

Necessário: 2× 221-412 + 2× 221-413. A lista pede 4× + 2× — os 2× 221-412
extras ficam como reserva.

## Links e modelos confirmados para compra

Itens com modelo, especificações e fornecedor já verificados e aprovados para o projeto:

| ID | Item | Qtd | Modelo validado | Fornecedor / Link |
|---|---|---|---|---|
| E3 | Conector de emenda com alavanca 2 vias | 4 | WAGO Legítimo 221-412 (até 4 mm²) | [Casa do Eletricista](https://www.casadoeletricistasc.com.br/con-p-emenda-c-alav-2-fios-4mm-wago/p/4134) |
| E4 | Conector de emenda com alavanca 3 vias | 2 | WAGO Legítimo 221-413 (até 4 mm²) | [Casa do Eletricista](https://www.casadoeletricistasc.com.br/con-p-emenda-c-alav-3-fios-4mm-wago/p/4135) |
| E12 | Porta-fusível in-line 5×20 mm com rabicho | 1 | Modelo F-103C cilíndrico aéreo | [Eletrodex](https://www.eletrodex.net/acessorios/passivos/fusiveis/porta-fusivel-meio-de-fio-f103c) |

## Sequenciamento

Comprar **E2, M1 e E10 primeiro**, isoladamente se necessário. Os três definem
recortes e furos do gabinete, e o modelo mecânico está em análise. Medir as
peças reais e reconciliar com o modelo antes da aprovação evita um ciclo de
impressão de peça grande.

O restante pode ir em compra única.

## Verificação pós-compra

- [ ] E2 é 10 A (pinos de 4,0 mm), não 20 A
- [ ] ⌀ externo de E1 está dentro da faixa de aperto de E10 (≤ 8 mm para PG9)
- [ ] ~~E11 tem marcação "HLK-PM01" e "5V" impressa no corpo~~ (fora de uso, #306)
- [ ] E1 tem bitola **1,0 mm²** indicada na capa
- [ ] ~~E13 é **10 A retardado (T)**, não ação rápida e não 500 mA~~ (fora de uso, #307)
- [ ] ~~E12 é **in-line com rabicho**, não de painel~~ (fora de uso, #307)
- [ ] Recorte de E2 (`tomada_j1`), furo de M1 (`furo_botao`, hoje 16 mm no
      modelo) e corpo de E10 (`prensa_cabo` Ø15) medidos e enviados para o
      modelo mecânico
- [ ] M4 permite soquetar o DevKit de 30 pinos (15 por lado após corte)
- [ ] Plugue da CP30: confirmar se o 3º pino (terra) está populado

**Revisão B (registrar com foto da etiqueta, na issue indicada):**

- [ ] B1 SSR-40DA: etiqueta, corrente de entrada a 3,3 V e fuga desligado (#305)
- [ ] B2 dissipador: dimensões externas, aletas e furação (#305 → #302)
- [ ] B3 A1385: etiqueta (#306)
- [ ] B5 disjuntor: fabricante, modelo, curva, capacidade de interrupção, fixação (#307)
- [ ] B6 botão RGB: nome comercial, polaridade do LED, resistor interno, furo (#308)
- [ ] B7 buzzer: ativo ou passivo, tensão e corrente (#311)

## Pendências para o profissional habilitado (marco de 02/10)

- Bondar ao PE o corpo do botão RGB, se for metálico (lado da baixa tensão), ou
  mantê-lo só no SELV confiando na barreira da fonte A1385 + divisória?
  (Revisão B: o conector USB de painel foi descartado, #309. Na revisão A a
  pergunta era sobre o botão M1 e a barreira do HLK-PM01.)
- Revisão B: verificar a ligação da rede direto nos pinos da A1385 (decisão do
  responsável, #306), a coordenação disjuntor × SSR × condutores (#307) e a
  corrente de fuga do SSR desligado (#305).
- Declarar na defesa: o módulo pressupõe alimentação por circuito com
  **DR 30 mA** a montante (exigência NBR 5410 para circuito de cozinha).
