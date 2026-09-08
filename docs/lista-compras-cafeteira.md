# Lista de compras — Cafeteira Conectada

Projeto: retrofit IoT da cafeteira Britânia CP30 (800 W, 127 V, ~6,3 A).
Revisão: 2026-09-08. Prazo de defesa: 2026-10-16.

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

## Comprar — P1 · Rede elétrica 127 V

| ID | Item | Qtd | Busca | Exigido | Rejeitar |
|---|---|---|---|---|---|
| E1 | Cabo de força com plugue | 1 | `cabo de força tripolar 3G1,0mm 2P+T NBR 14136 1,5m` | 2P+T, 10 A, **condutor 1,0 mm²**, comprimento 1,2–2,0 m, **⌀ externo ≤ 8 mm** (tem de entrar no PG9 do E10) | Bitola 0,75 mm²; **1,5 mm² (⌀ externo grande demais para PG9)**; plugue sem terra; padrão americano/europeu |
| E2 | Tomada fêmea de painel `MEDIR` | 1 | `tomada femea painel 2P+T 10A NBR 14136` | **10 A** (pinos 4,0 mm), fixação em painel ou embutir | Versão 20 A (pinos 4,8 mm); tomada de parede sem furação frontal |
| E3 | Conector de emenda com alavanca | 4 | `conector emenda WAGO 221-412 2 vias` | 2 vias, até 4 mm², com alavanca | Modelos push-in sem alavanca (773/2273) |
| E4 | Conector de emenda com alavanca | 2 | `conector emenda WAGO 221-413 3 vias` | 3 vias, até 4 mm², com alavanca | Idem E3 |
| E5 | Cabo flexível preto | 1 m | `cabo flexivel 1,5mm 750V preto` | 1,5 mm², isolação 750 V | 300 V; cabo rígido |
| E6 | Cabo flexível azul | 1 m | `cabo flexivel 1,5mm 750V azul` | Idem E5 | Idem E5 |
| E7 | Cabo flexível verde-amarelo | 1 m | `cabo flexivel 1,5mm 750V verde amarelo` | Idem E5 | Verde liso (terra deve ser verde-amarelo) |
| E8 | Terminais tubulares pré-isolados | 20 | `terminal tubular ilhos pre-isolado 1,5mm` | Para 1,5 mm², pré-isolado | Terminal olhal, garfo ou pino chato |
| E9 | Termorretrátil | 1 m ⌀2,5 + 1 m ⌀4 | `tubo termorretratil 2,5mm 4mm kit` | Razão 2:1 | — |
| E10 | Prensa-cabo `MEDIR` | 1 | `prensa cabo PG9 nylon com porca` | PG9, faixa de aperto compatível com o ⌀ externo de E1 | Metálico (rosca em plástico impresso); PG7 ou PG11 sem conferir o cabo |
| E11 | Módulo AC-DC | 2 | `HLK-PM01 fonte AC-DC 5V 3W` | Saída 5 V, 600 mA, entrada 100–240 V. **1 em uso + 1 reserva** (não é arquitetura de duas fontes) | HLK-PM03 (3,3 V); HLK-PM12 (12 V); HLK-5M05 (formato diferente) |
| E12 | Porta-fusível **in-line** | 1 | `porta fusivel 5x20 in-line com rabicho` | Para fusível 5×20 mm, **corpo fechado, com rabicho, para fixação por abraçadeira** | **Porta-fusível de painel** (exige furo que o modelo mecânico proíbe); de PCB; de engate rápido |
| E13 | Fusível de vidro | 5 | `fusivel vidro 5x20 10A retardado` | 5×20 mm, **10 A**, retardado (tipo T) | Ação rápida (F); ≤ 6,3 A (abre com a carga); acima de 10 A |
| E14 | Borne de aterramento DIN | 1 | `borne aterramento trilho DIN verde amarelo 4mm` | Verde-amarelo, para trilho DIN 35 mm, 4–6 mm² | Borne comum (não identificado como terra); tipo parafuso de chapa |
| E15 | Trilho DIN + travas | 1 | `trilho DIN 35mm + trava fim de trilho` | ~5 cm de trilho 35 mm + 2 travas de fim de trilho | — (alternativa: dispensar e projetar clipe no boss impresso — decisão do modelo mecânico) |

**Intenção de projeto de E12/E13 (revisada 08/09/2026):** F1 protege **todo o
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
  é **active-low** / gatilho de nível BAIXO — HL-52S)
- LED RGB 5 mm, pushbutton de protoboard, resistores 220 Ω e 10 kΩ, protoboard
  MB-102, jumpers, cabo USB — kit LAFVIN
- Filamento PETG e ASA — Alfabra
- Estanho para solda — Alfabra
- Alicate de crimpar terminal tubular — Alfabra
- Termômetro IR / termopar — empréstimo Alfabra

---

## Contagem de conectores (derivada do esquemático)

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

## Sequenciamento

Comprar **E2, M1 e E10 primeiro**, isoladamente se necessário. Os três definem
recortes e furos do gabinete, e o modelo mecânico está em análise. Medir as
peças reais e reconciliar com o modelo antes da aprovação evita um ciclo de
impressão de peça grande.

O restante pode ir em compra única.

## Verificação pós-compra

- [ ] E2 é 10 A (pinos de 4,0 mm), não 20 A
- [ ] ⌀ externo de E1 está dentro da faixa de aperto de E10 (≤ 8 mm para PG9)
- [ ] E11 tem marcação "HLK-PM01" e "5V" impressa no corpo
- [ ] E1 tem bitola **1,0 mm²** indicada na capa
- [ ] E13 é **10 A retardado (T)**, não ação rápida e não 500 mA
- [ ] E12 é **in-line com rabicho**, não de painel
- [ ] Recorte de E2 (`tomada_j1`), furo de M1 (`furo_botao`, hoje 16 mm no
      modelo) e corpo de E10 (`prensa_cabo` Ø15) medidos e enviados para o
      modelo mecânico
- [ ] M4 permite soquetar o DevKit de 30 pinos (15 por lado após corte)
- [ ] Plugue da CP30: confirmar se o 3º pino (terra) está populado

## Pendências para o profissional habilitado (marco de 02/10)

- Bondar ao PE o botão metálico M1 e o conector USB (lado da baixa tensão), ou
  mantê-los só no SELV confiando na barreira do HLK-PM01 + divisória?
- Declarar na defesa: o módulo pressupõe alimentação por circuito com
  **DR 30 mA** a montante (exigência NBR 5410 para circuito de cozinha).
