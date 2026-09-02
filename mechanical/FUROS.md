# Catálogo de furos e aberturas — módulo físico

Propósito de cada furo/abertura dos modelos. Coordenadas no sistema das peças
(face superior do tampo em `Z = 0`; `X` ao longo de `pegada_x`; `Y = 0` = saia
frontal / baixa tensão; `Y = pegada_y` = saia traseira / rede; `X = 0` = lateral
esquerda). Espelhado nos FCStd pelos objetos `notas_furos` e pelo campo
**Descrição** (`Label2`) de cada objeto.

Valores são dos parâmetros arbitrados atuais — o revisor da empresa ajusta na
planilha `params` e a geometria acompanha por expressão.

---

## Peça 1 — aberturas de face

Centro a 22 mm abaixo do tampo (`abertura_centro_z`).

| Objeto | Forma | Face | Centro (x, y, z) | Propósito |
|---|---|---|---|---|
| `janela_rf` | retângulo 40 × 28 | frontal | (75, 0, −22) | **Saída de rádio.** Deixa a antena Wi-Fi do ESP32 (encostada por dentro, ~5 mm, sem metal na frente) enxergar para fora. Não é acesso a componente. Dimensão cobre a antena PCB do DevKit V1 com margem. Valida no ensaio 11 (atenuação de Wi-Fi). |
| `furo_led` | Ø5 | frontal | (150, 0, −22) | **Sinalização.** Passagem do LED RGB de status, montado na placa auxiliar da faixa de baixa tensão. Ø nominal — conferir contra o LED/porta-LED real (item 5). |
| `furo_botao` | Ø12 | frontal | (195, 0, −22) | **Comando.** Passagem do botão de painel. Ø nominal — conferir contra o botão real (item 5). |
| `prensa_cabo` | Ø15 | traseira | (70, 210, −22) | **Entrada de energia do módulo** (rede 127 V vinda da parede). Recebe o prensa-cabo PG9 — alívio de tração normalizado que a inspeção elétrica procura. Escolhido em vez de passa-fio ou C14: menor recorte numa saia que já perdeu a tomada J1. Faixa de aperto 4–8 mm nominal vs cabo real (item 14). |
| `tomada_j1` | retângulo 45,5 × 23 | traseira | (185, 210, −22) | **Saída de energia.** Recorte do módulo de tomada 2P+T (NBR 14136) onde a **cafeteira** é plugada; o módulo comuta essa tomada pelo relé. Girado 90°: 45 mm de recorte numa saia de 45 mm não deixaria material; girado sobram ~10 mm acima e abaixo. Travamento das garras em chapa 1,2 mm a verificar (item 1). |
| `recorte_usb` | retângulo ~16 × 9 | lateral esq. | (0, 37,5, −22) | **Gravação / debug.** Passagem do conector USB de painel, ligado ao micro-USB do ESP32 por cabo de painel. Esse cabo desacopla a posição do conector da da placa — no DevKit V1 antena e micro-USB ficam em extremidades opostas, então sem ele "USB acessível" e "Wi-Fi decente" seriam exclusivos. Dimensão **placeholder** — medir o flange real (item 16). |

## Peça 1 — rasgos de ventilação

**Função térmica.** 25 × 3 mm, centro a 15 mm da borda inferior da saia
(`rasgo_altura`). Com os pés, impedem a caixa de virar forno selado e deixam o
fundo trocar calor com o vão de ar — a fonte de calor está em cima e o topo é
fechado, então não há efeito chaminé. **3 mm bloqueia dedo, não bloqueia arame
fino → bornes com tampa na faixa de rede são obrigatórios.**

| Objeto | Face | Centro y | Observação |
|---|---|---|---|
| `rasgo_e_1` | lateral esq. | 71,25 | |
| `rasgo_e_2` | lateral esq. | 105 | |
| `rasgo_e_3` | lateral esq. | 138,75 | |
| `rasgo_e_4` | lateral esq. | 172,5 | |
| `rasgo_d_0` | lateral dir. | 37,5 | posição que na esquerda é do `recorte_usb` |
| `rasgo_d_1..4` | lateral dir. | 71,25 / 105 / 138,75 / 172,5 | |

A esquerda tem 4 (o USB ocupa a 5ª posição da grade); a direita tem 5. A
assimetria é proposital — não corrigir.

## Peça 1 — furação

| Objeto (padrão) | Ø / eixo | Qtd | Propósito |
|---|---|---|---|
| `fix_<e\|d><f\|t>_<lo\|hi>` | Ø3,2 (M3) / eixo X | 8 (2 por canto) | **Montagem da caixa.** Prende a aba de canto (que sai da saia frontal/traseira) contra a face interna da lateral, fechando o corpo. Parafuso M3 inox (premissa) ou rebite (alternativa paramétrica) — alumínio em inox forma par galvânico e aqui derrame de café é rotina (item 9). Alturas a 1/3 e 2/3 da saia — provisórias. |
| `pr_<f\|t\|e\|d><a\|b>` | Ø~4,5 / eixo Z | 8 (2 por aba) | **Fixação do fundo, lado Peça 1.** Recebe a porca-rebite M3 nas abas de retorno, onde os parafusos da Peça 2 entram. Ø de furo da porca-rebite para chapa 1,2 mm: ~4,5 mm nominal — confirmar item 17. |

Centros `fix_*`: x ≈ 1,2 / 258,8 · y ≈ 11,2 / 198,8 · z ≈ −30,4 (lo) / −15,8 (hi).
Centros `pr_*`: gerados por `_env.porca_rebite_holes` — x ∈ {7,2 · 86,7 · 173,3 ·
252,8}, y ∈ {7,2 · 70 · 140 · 202,8}, z ≈ −44,4.

## Peça 1 — o que **não** tem furo

- **Tampo:** nenhuma abertura (regra de derrame).
- **Fusível:** porta-fusível em linha, corpo fechado, preso por abraçadeira.
  Nenhum furo em chapa — troca abrindo o fundo.
- **Travessia de 5 fios** (+5 V/GND do HLK-PM01 + VCC/GND/IN do relé): rasgo com
  passa-fio na **divisória** de policarbonato, perpendicular a ela — não na
  chapa. A divisória não está modelada.
- `relief_*`: alívios de canto (entalhe no encontro das dobras), não furos.

---

## Peça 2 — fundo plano

| Objeto (padrão) | Ø / eixo | Qtd | Propósito |
|---|---|---|---|
| `fundo_furo_1..8` | Ø3,2 (M3) / eixo Z | 8 | **Fixação do fundo, lado Peça 2.** Furo de **passagem** do parafuso M3 (a porca-rebite fica na aba da Peça 1). Coincidem com os `pr_*` — mesmas fórmulas em `_env.porca_rebite_holes`, `check_peca2.py` confirma. Abrir o fundo = trocar o fusível e acessar o interior. |
| `cant_furo_1..2` | Ø3,2 (M3) / eixo Z | 2 | **Separação elétrica.** Fixam as cantoneiras que seguram a divisória (policarbonato ou acrílico, altura plena) na linha `y = espessura + faixa_baixa` ≈ 85, entre a faixa de rede (127 V, traseira) e a de baixa tensão (5 V, frontal). Nenhum condutor de rede a menos de 10 mm da divisória — valida com o eletricista (marco 02/10). |
| `notch_<f\|t><l\|r>` | recorte, não furo | 4 | Alívio para as abas de canto da Peça 1, que descem pelo interior e ocupariam a quina do fundo. |

Centros `fundo_furo_*` = mesmos (x, y) dos `pr_*`. Centros `cant_furo_*`:
(65, 85,2) e (195, 85,2).
