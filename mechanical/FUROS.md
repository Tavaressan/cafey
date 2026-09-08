# Catálogo de furos e aberturas — módulo físico

**Nota (issue #116 — impressão 3D):** este catálogo descreve a versão em
chapa dobrada (`build_peca1.py`/`build_peca2.py`, histórico). A versão atual,
impressa em FDM (`build_peca1_3d.py`/`build_peca2_3d.py`), recria **as mesmas
aberturas, nas mesmas coordenadas (x, y, z)** — só os nomes de objeto mudam
(`saia_*` → `parede_*`; `fix_*`/`pr_*` → `boss_*`/`insert_*`, que agora
resolvem a fixação Peça1/Peça2 por insert térmico em vez de porca-rebite; não
há mais furo de canto porque a peça impressa já nasce fechada nos 4 cantos).
Ver `_env3d.NOTAS_FUROS_P1`/`NOTAS_FUROS_P2` (texto embutido no FCStd) e a
seção "Variante para impressão 3D" de `mechanical/README.md` para o
detalhamento da fixação nova. As tabelas abaixo continuam válidas para as
posições x/y das aberturas de face e dos rasgos — só a peça de fixação dos
cantos deixou de existir.

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
| `furo_botao` | Ø16 | frontal | (195, 0, −22) | **Comando.** Passagem do botão de painel metálico momentâneo NA de 16 mm (revisão 08/09/2026; era Ø12). Ø nominal — conferir contra o botão real (M1 da lista de compras). |
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
- **Fusível:** porta-fusível 5×20 mm **em linha** (com rabicho), corpo fechado,
  preso por abraçadeira. Nenhum furo na parede — troca abrindo o fundo.
  Confirmado na revisão de 08/09/2026 (item E12 da lista de compras): **não**
  usar porta-fusível de painel. F1 = 10 A T, protege a carga.
- **Travessia de 5 fios** (+5 V/GND do HLK-PM01 + VCC/GND/IN do relé): rasgo com
  passa-fio na **divisória** de policarbonato, perpendicular a ela — não na
  chapa. A divisória não está modelada.
- `relief_*`: alívios de canto (entalhe no encontro das dobras), não furos.

---

## Peça 2 — fundo plano (tampa por baixo)

Chapa plana rente à face externa das saias (260 × 210), **parafusada na face
inferior das abas de retorno da Peça 1**. Sai por baixo para manutenção — um
fundo apoiado por dentro sobre as abas viradas para dentro ficaria preso (o vão
livre entre as bordas internas das abas é 233,6 × 183,6, menor que o fundo).
Face superior coplanar com a face inferior das abas (`Z = −altura_externa`);
protrai `espessura_fundo` (1,2 mm) abaixo da borda das saias. Os pés montam nela.

| Objeto (padrão) | Ø / eixo | Qtd | Propósito |
|---|---|---|---|
| `fundo_furo_1..8` | Ø3,2 (M3) / eixo Z | 8 | **Fixação do fundo, lado Peça 2.** Furo de **passagem**: o parafuso entra por baixo e rosqueia na porca-rebite `pr_*` da aba da Peça 1. Coincidem com os `pr_*` — mesmas fórmulas em `_env.porca_rebite_holes`, `check_peca2.py` confirma. Cabeça panela no vão de ar entre os pés. Abrir o fundo = trocar o fusível e acessar todo o interior. |
| `cant_furo_1..2` | Ø3,2 (M3) / eixo Z | 2 | **Separação elétrica.** Fixam as cantoneiras que seguram a divisória (policarbonato ou acrílico, altura plena) na linha `y = espessura + faixa_baixa` ≈ 85, entre a faixa de rede (127 V, traseira) e a de baixa tensão (5 V, frontal). Nenhum condutor de rede a menos de 10 mm da divisória — valida com o eletricista (marco 02/10). |

Centros `fundo_furo_*` = mesmos (x, y) dos `pr_*`. Centros `cant_furo_*`:
(65, 85,2) e (195, 85,2).

---

## Variante impressa (FDM) — fixação Peça 1 / Peça 2

Substitui `fix_*` (parafuso de canto — não existe mais, peça já nasce
fechada) e `pr_*`/`fundo_furo_*` (porca-rebite) da chapa. Mesmas 8 posições
(x, y) — geradas por `_env3d.insert_holes`, layout idêntico ao antigo
`_env.porca_rebite_holes`.

| Objeto (padrão) | Ø / eixo | Qtd | Propósito |
|---|---|---|---|
| `boss_<f\|t\|e\|d><a\|b>` | Ø9 (boss_d) / eixo Z | 8 | Reforço cilíndrico na flange, altura `insert_prof` (6 mm) — mais alto que a flange (2,4 mm) para caber o insert. Motivo do ajuste de z dos componentes `apoio=fundo` em `componentes_3d.csv` (sobem para standoff acima do boss). |
| `insert_<f\|t\|e\|d><a\|b>` | Ø4,0 (insert_furo) / eixo Z | 8 | Furo cego pela face inferior, profundidade 6 mm. Aloja o insert térmico M3×5,7 fundido a quente após a impressão. |
| `fundo_furo_1..8` (Peça 2) | Ø3,4 (furo_passagem) / eixo Z | 8 | Folga do parafuso M3 na Peça 2 — mesma lógica de montagem da chapa (parafuso entra por baixo, rosqueia no insert). |
