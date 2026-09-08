# mechanical — módulo físico (pedestal)

Modelo paramétrico do pedestal de `docs/status-modulo-fisico.md`.

**Impressão 3D (atual, issue #116):** ver a seção
[Variante para impressão 3D (FDM)](#variante-para-impressão-3d-fdm-issue-116)
abaixo — `build_peca1_3d.py` / `build_peca2_3d.py` e os scripts `*_3d.py`. A
seção seguinte, sobre chapa dobrada, fica preservada como histórico de decisão
(o modelo em chapa não é mais o alvo de fabricação, mas os scripts continuam
funcionando e não foram removidos).

## Chapa dobrada (histórico — substituída pela variante 3D acima)

- **Peça 1** — peça dobrada (tampo + 4 saias + 4 abas de fundo + 4 abas de canto).
- **Peça 2** — fundo plano removível (tampa por baixo).
- **Montagem** — as duas peças na posição de projeto.
- **Arranjo** — montagem + divisória + volumes de referência dos componentes
  (caixas envolventes **estimadas**, em `params/componentes.csv`).

## Abordagem

Sólidos `Part` paramétricos, 100% por script headless, dirigidos pelo Spreadsheet
`params`. Sem o addon SheetMetal: a planificação é analítica
(`bend deduction = 2·setback − BA`), auditável e independente do unfolder frágil.
O SheetMetal fica reservado para quando o revisor da empresa exigir árvore
sheet-metal nativa ou unfold validado pela ferramenta dele.

Parâmetros de processo (`espessura`, `raio_dobra`, `fator_k`, `material`) são
**placeholders arbitrados** — é a planilha `params` que o revisor corrige, e a
geometria depende dela por expressão.

## Coordenadas

Face superior do tampo em `Z = 0`; a peça desce para `Z < 0`. `X` ao longo de
`pegada_x`, `Y` ao longo de `pegada_y`. `Y = 0` é a saia frontal (baixa tensão),
`Y = pegada_y` a traseira (rede). `X = 0` é a lateral esquerda. As duas peças
compartilham este sistema, então a Peça 2 entra na montagem sem deslocamento.

## Scripts

```
freecadcmd mechanical/scripts/build_peca1.py     # build/peca1.FCStd
freecadcmd mechanical/scripts/unfold_peca1.py    # tabela de dobras -> Spreadsheet 'planificacao'
freecadcmd mechanical/scripts/check_peca1.py     # validação Peça 1
freecadcmd mechanical/scripts/export_step.py     # build/peca1.step
freecadcmd mechanical/scripts/build_peca2.py     # build/peca2.FCStd + peca2.step
freecadcmd mechanical/scripts/check_peca2.py     # validação Peça 2 + coincidência de furos
freecadcmd mechanical/scripts/build_montagem.py  # build/montagem.FCStd + montagem.step
freecadcmd mechanical/scripts/check_montagem.py  # validação de conjunto (pilha de parafuso, cobertura, elevação)
freecadcmd mechanical/scripts/build_arranjo.py   # build/arranjo.FCStd + arranjo.step (divisória + volumes de referência)
freecadcmd mechanical/scripts/check_arranjo.py   # valida faixas, altura livre, separação rede/baixa
freecadcmd mechanical/scripts/flatten_peca1.py   # build/peca1_plano.FCStd (contorno planificado)
freecadcmd mechanical/scripts/export_dxf.py      # build/peca1_plano.dxf + peca2_plano.dxf
```

Ordem: `build_peca1` → `build_peca2` → `build_montagem` → `check_montagem` →
`build_arranjo` → `check_arranjo`;  `flatten_peca1` → `export_dxf`. `freecadcmd` = `/Applications/FreeCAD.app/Contents/Resources/bin/freecadcmd`
(FreeCAD 1.1.3).

`_build.py` reúne o Spreadsheet e as primitivas; `_env.py` reúne paths, fórmulas
(BA, setback, segmentos do blank, furos de porca-rebite) e as descrições
(`DESCRIPTIONS`). Cada objeto do FCStd carrega `Label` + `Label2` (campo
"Descrição"); os objetos `notas_fabricacao` e `notas_furos` reúnem o que não é
geometria. **[`FUROS.md`](FUROS.md)** — catálogo do propósito de cada furo e
abertura, espelhado no `notas_furos` de cada FCStd.

## Entregáveis (`build/`, fora do versionamento)

| Arquivo | Conteúdo |
|---|---|
| `peca1.FCStd` / `peca1.step` | Peça 1, sólido paramétrico |
| `peca2.FCStd` / `peca2.step` | Peça 2, fundo plano |
| `montagem.FCStd` / `montagem.step` | Conjunto (snapshot; `check_montagem.py` valida interferência = 0, pilha de parafuso, cobertura das abas, elevação) |
| `arranjo.FCStd` / `arranjo.step` | Montagem + divisória + 13 volumes de referência; `check_arranjo.py` valida faixas, altura livre, separação rede/baixa |
| `peca1_plano.FCStd` | Contorno planificado da Peça 1 |
| `peca1_plano.dxf` | **Contorno externo** da Peça 1 (365,4 × 315,4 mm) — sem aberturas |
| `peca2_plano.dxf` | Peça 2 **completa** (contorno 260 × 210 + 8 furos M3 + 2 furos de cantoneira) |

## Peça 2 — fundo (tampa por baixo)

- Chapa plana rente à face externa das saias (260 × 210), **parafusada na face
  inferior das abas de retorno da Peça 1**. Sai por baixo para manutenção — um
  fundo apoiado por dentro sobre as abas viradas para dentro ficaria preso
  (vão livre entre as abas 233,6 × 183,6 < fundo). Face superior coplanar com a
  face inferior das abas; protrai `espessura_fundo` abaixo da borda das saias.
  Os pés montam nela.
- 8 furos de passagem M3 gerados pelas **mesmas fórmulas** das porcas-rebite da
  Peça 1 (`_env.porca_rebite_holes`); o parafuso entra por baixo e rosqueia na
  porca-rebite da aba. `check_peca2.py` confirma a coincidência.
- 2 furos para as cantoneiras da divisória, na linha `y = espessura + faixa_baixa`
  (policarbonato/acrílico, altura plena, entre a faixa de rede e a de baixa tensão).

## Arranjo interno (`arranjo.FCStd`)

`build_arranjo.py` monta Peça 1 + Peça 2 + a divisória + um volume de referência
por componente, lido de **`params/componentes.csv`**. Cada volume é uma **caixa
envolvente estimada** — o usuário confirma os tamanhos editando o CSV e rodando
de novo. Cada objeto carrega `Label2` com a origem da estimativa (datasheet do
HLK-PM01, dimensão de mercado, dado do documento…).

Layout: faixa da rede (127 V) na traseira, divisória de 3 mm, faixa de baixa
tensão (5 V) na frente. HLK-PM01 e módulo de relé do lado da rede com os
terminais de baixa tensão voltados para a divisória; travessia única de 5 fios
(`travessia_fios`) por um rasgo de passa-fio na divisória. ESP32 com o eixo longo
frente-fundo, antena atrás da `janela_rf`.

`check_arranjo.py` valida: envelope vertical, componente dentro das paredes, sem
penetrar a chapa, separação de faixa (rede ≥ 10 mm da divisória), só a travessia
cruza o plano da divisória, ESP32 alinhado com a janela de RF, e reporta
interferências entre volumes.

**Achado do 1º passe (tamanhos estimados):** a divisória a "altura plena" e os
componentes altos (`borne_rede` ~34 mm) deixam pouca folga sob o tampo — a
divisória (40 mm) fica a **2,6 mm** do tampo. Altura interna útil ~43,8 mm.
Confirmar as alturas reais e decidir a folga da divisória.

## Planificação e DXF — limites

O `peca1_plano.dxf` é **só o contorno** (mais alívios e abas de canto). As
aberturas por face **não entram**: a posição planificada de cada abertura depende
do fator K real da ferramenta da empresa (consultas 6 e 7 da lista de
verificação). O contorno serve para dimensionar a chapa bruta (~400 × 350 mm) e
como base para o revisor aplicar o unfold da ferramenta dele.

Aberturas, na referência da peça dobrada (do documento), para o desenho cotado:

| Face | Abertura | Posição |
|---|---|---|
| Frontal | Janela RF 40×28 | x=75, centro 22 mm abaixo do tampo |
| Frontal | LED Ø5 | x=150 |
| Frontal | Botão Ø12 | x=195 |
| Traseira | Prensa-cabo PG9 Ø15 | x=70 |
| Traseira | Tomada J1 45,5×23 | x=185 |
| Laterais | Rasgos 25×3 | 15 mm da borda inferior (4 na esq. + USB, 5 na dir.) |

## Estado / limitações

- **Sem raios de dobra** nos sólidos (arestas vivas). Cosméticos para o STEP; a
  linha neutra real está na planificação analítica.
- `usb_w`, `usb_h`: placeholders — medir o flange do conector (item 16).
- `espessura_fundo`: arbitrado; confirmar a chapa.
- Furos de canto e das cantoneiras em posições provisórias.
- Parafusos do fundo: cabeça panela (saliente ~2 mm no vão de ar). Sem escareado
  — chapa de 1,2 mm é fina para escarear M3; confirmar com a empresa se quiser flush.
- `montagem.FCStd` é snapshot (cópia de forma), não assembly vivo.

## Notas de fabricação (chapa)

- **Ordem de dobra:** abas do fundo primeiro, com a chapa plana; saias depois. A
  viradeira não alcança o interior de uma caixa de 45 × 260 mm.
- Alívio de canto de `raio_dobra + espessura` (~2,4 mm) no encontro das dobras.
- Porta-fusível em linha preso por abraçadeira — nenhum furo em chapa.
- Alumínio em inox forma par galvânico; parafuso/rebite de inox (consulta 9).

---

## Variante para impressão 3D (FDM) — issue #116

Reformulação da mecânica para impressão 3D no lugar de chapa dobrada. Mesma
pegada (260 × 210 mm), mesma **cavidade útil** (40,2 mm) e mesmo arranjo
interno (`params/componentes_3d.csv`). A **altura externa cresceu de 45 para
53 mm**: os 8 mm extras (`reforco_delta`) são uma banda de nervura sob o
tampo — o subconjunto interno (piso + componentes de `apoio=fundo` + divisória)
desceu junto, translação rígida, sem novo arranjo. O que muda:

- **Peça 1 vira um sólido único** (tampo + 4 paredes + flange de fixação
  contínua) em vez de 8 dobras + 4 abas de canto — os 4 cantos já nascem
  fechados, sem fator K, sem linha neutra, sem planificação.
- **Parede 2,4 mm** (3 perímetros de bico 0,4 mm), contra 1,2 mm da chapa —
  mínimo prático para uma parede que é estrutura primária em FDM.
- **Reforço estrutural sob o tampo** (audit da carga de 3 kg): 4 colunas de
  canto tampo→fundo + nervura perimetral + nervura transversal na linha da
  divisória. Tudo paramétrico (`coluna_qtd`, `nervura_transversal`,
  `nervura_h`). Ver "Reforço estrutural" abaixo.
- **Fixação Peça 1 / Peça 2 por insert térmico rosqueado M3** (fundido a
  quente em 8 bosses da flange, `boss_d` = 9 mm, furo do insert `insert_furo`
  = 4,0 mm, profundidade `insert_prof` = 6 mm), no lugar da porca-rebite de
  chapa. O parafuso da Peça 2 continua entrando por baixo.
- **STL exportável** (`export_stl.py`), novo requisito da issue.
- Todas as aberturas do catálogo (`FUROS.md`) recriadas 1:1 na nova geometria.

### Scripts

```
freecadcmd mechanical/scripts/build_peca1_3d.py     # build/peca1_3d.FCStd
freecadcmd mechanical/scripts/check_peca1_3d.py     # validação Peça 1 (geometria, margens, flecha do tampo)
freecadcmd mechanical/scripts/export_step_3d.py     # build/peca1_3d.step
freecadcmd mechanical/scripts/build_peca2_3d.py     # build/peca2_3d.FCStd + peca2_3d.step
freecadcmd mechanical/scripts/check_peca2_3d.py     # validação Peça 2 + coincidência dos furos com os inserts
freecadcmd mechanical/scripts/check_montagem_3d.py  # validação de conjunto (sem build_montagem_3d.py: confere as duas Shapes direto)
freecadcmd mechanical/scripts/build_arranjo_3d.py   # build/arranjo_3d.FCStd + arranjo_3d.step
freecadcmd mechanical/scripts/check_arranjo_3d.py   # valida faixas, altura livre, separação rede/baixa, sem colisão com os bosses
freecadcmd mechanical/scripts/export_stl.py         # build/peca1_3d.stl + peca2_3d.stl (Peça 1 e Peça 2)
```

Ordem: `build_peca1_3d` → `build_peca2_3d` → `check_peca1_3d` → `check_peca2_3d`
→ `check_montagem_3d` → `build_arranjo_3d` → `check_arranjo_3d` →
`export_step_3d` / `export_stl`.

`_env3d.py` é o equivalente de `_env.py` para esta variante: paths de saída
(`peca1_3d.*`, `peca2_3d.*`, `arranjo_3d.*`), `DESCRIPTIONS`/notas de
fabricação, `insert_holes()` (posição dos 8 bosses/insertes, mesmo layout dos
antigos `porca_rebite_holes()`), `coluna_positions()` / `pe_positions()` /
`y_nervura_transversal()` (geometria do reforço) e `deflexao_tampo_mm(g,
com_reforco=)` — flecha de longo prazo do tampo (elástica × `fator_fluencia`),
placa ret. simpl. apoiada (Roark). `_build.py` (helpers `box`/`cyl`) é
reaproveitado sem mudança.

### Parâmetros (`params/parametros_3d.csv`)

Cópia de `parametros.csv` sem os campos de dobra (`raio_dobra`, `fator_k`,
`recuo_aba`, `alivio_canto`), com `espessura` renomeado para `parede` (2,4 mm)
e os novos parâmetros de fixação: `boss_d`, `insert_furo`, `insert_prof`,
`furo_passagem`. `aba_fundo` sobe de 12 para 14 mm (a flange também aloja o
boss do insert, Ø 9 mm). Adicionados no audit da carga de 3 kg: `altura_externa`
45→53, `reforco_delta`, `nervura_h`/`nervura_w`/`nervura_transversal`,
`coluna_qtd`/`coluna_lado`, `travessia_folga`, `pe_d`/`pe_inset`,
`carga_operacao` (3,0 kgf), `fator_fluencia` (4,0), `folga_comp_reforco`.

### Reforço estrutural sob o tampo (audit da carga de 3 kg)

O modelo inicial do #116 só validava a flecha **elástica** do tampo sob
2,9 kg e passava raspando (0,57 mm vs. limite `L/300` = 0,70 mm). O audit
apontou dois furos: (1) faltava a **fluência** — PETG sob carga contínua +
calor irradiado deforma 3–4× a flecha elástica ao longo de meses; (2) o
pedido é **3 kg**, não 2,9. Com `fator_fluencia = 4` e `carga_operacao = 3,0`,
o tampo **liso** dá flecha de longo prazo ≈ **2,4 mm** — estoura o limite.

Reforço adicionado, todo paramétrico em `parametros_3d.csv`:

| Elemento | Param | Função |
|---|---|---|
| 4 colunas de canto (tampo → plano do fundo) | `coluna_qtd` (4), `coluna_lado` (12) | Caminho de carga vertical direto aos pés; travam o corpo contra racking |
| Nervura perimetral sob o tampo | `nervura_h` (6), `nervura_w` (3) | Enrijece a borda do tampo e o topo das paredes |
| Nervura transversal (linha da divisória) | `nervura_transversal` (1) | Divide o vão do tampo em 2 painéis → vão efetivo 210 → **118 mm**, flecha ÷ ~10 |
| Assentos de pé coaxiais com as colunas | `pe_d` (15) na Peça 2 | Fecham o caminho tampo → coluna → fundo → pé → bancada |

Resultado (`check_peca1_3d.py`): flecha de longo prazo **com** reforço ≈
**0,24 mm** < `L/300` = 0,70 mm. A peça imprime de cabeça para baixo (tampo
na mesa) — nervuras e colunas crescem a partir do tampo, **sem suporte**.

`coluna_qtd = 0` e `nervura_transversal = 0` voltam ao modelo do #118 (nesse
caso rebaixar `altura_externa` de 53 para 45). **Estimativa analítica, não
FEA nem ensaio** — o ensaio de bancada com o protótipo impresso e a carga
real por semanas (fluência) é o que confirma.

### Massa e resistência mecânica — decisões e premissas

- **Material assumido: PETG** (`_env3d.MATERIAL_TXT`), não validado por
  ensaio — ver `docs/status-modulo-fisico.md` para a justificativa e a
  ressalva. Densidade usada apenas para uma estimativa de massa em **sólido
  cheio** (limite superior; o infill parcial do fatiador reduz a massa real).
- **Flecha do tampo sob carga de operação:** `_env3d.deflexao_tampo_mm(g,
  com_reforco=)` — flecha **de longo prazo** = elástica (placa ret. simpl.
  apoiada, Roark) × `fator_fluencia`. `check_peca1_3d.py` reporta o valor
  **sem** reforço (≈ 2,4 mm, referência do audit) e valida o valor **com**
  reforço (≈ 0,24 mm) contra `L/300` do menor vão. **Inferência de
  engenharia, não ensaio** — item 8/10 da lista de verificação.
- **Sem fillet nos cantos verticais** nesta primeira versão — decisão
  consciente (YAGNI): nenhum requisito de resistência levantado pela issue
  exige isso agora; fica como melhoria futura se o ensaio de bancada indicar
  necessidade.

### Arranjo interno (`arranjo_3d.FCStd`)

Mesmos 14 componentes de `componentes.csv`, mesma separação rede/baixa
tensão. Ajustes em `componentes_3d.csv`:
- Componentes de `apoio=fundo` em standoffs acima do topo dos bosses de
  insert (que ficam mais altos que a flange de 2,4 mm).
- Todo o subconjunto interno referenciado ao piso (`apoio` ∈ fundo /
  divisória / abraçadeira / livre / fios) **desceu 8 mm** (`reforco_delta`)
  com o crescimento de `altura_externa` 45→53 — abre a banda de nervura sob
  o tampo sem mexer nas folgas internas (standoff 6 mm, divisória→piso
  2,4 mm são as mesmas do #118).
- Divisória 2,4 mm mais estreita de cada lado (255,2 × 3 × 40) e agora com
  topo em `z=-10,6` — 2,2 mm abaixo da nervura transversal.

`check_arranjo_3d.py` confirma: zero interferência entre os 14 volumes, zero
penetração na peça impressa, **8,1 mm** de folga sob o tampo (a banda de
nervura); e `check_peca1_3d.py` valida que nenhuma nervura/coluna colide com
os volumes internos (folga mínima 2,1 mm ≥ `folga_comp_reforco`).

### Entregáveis (`build/`, fora do versionamento)

| Arquivo | Conteúdo |
|---|---|
| `peca1_3d.FCStd` / `.step` / `.stl` | Peça 1 impressa (sólido único) |
| `peca2_3d.FCStd` / `.step` / `.stl` | Peça 2, fundo plano impresso |
| `arranjo_3d.FCStd` / `.step` | Peça 1 + Peça 2 + divisória + 13 volumes de referência |

### Estado / limitações

- `usb_w`, `usb_h`: placeholders — medir o flange do conector (item 16, doc).
- `boss_d`/`insert_furo`/`insert_prof`: valores nominais de insert térmico
  M3x5,7 comum no mercado (ex. Ruthex/Boyard) — confirmar contra o insert
  realmente comprado antes de imprimir o protótipo.
- Sem fillet nos cantos verticais (ver acima).
- **`fator_fluencia` (4,0) é arbitrado** — faixa típica de termoplástico
  carregado, mas o valor real do PETG impresso sob a temperatura da base da
  cafeteira só sai de um ensaio de fluência de semanas (item 8/10). Se o
  ensaio der pior, subir `nervura_h` ou `parede`.
- **Flange na base imprime como ponte** de `aba_fundo` (14 mm) para dentro,
  sem chanfro 45° — aceitável para PETG a 14 mm, mas confirmar no protótipo;
  chanfro fica como melhoria paramétrica se reprovar.
- `pe_d`/`pe_inset`: assento de pé é só um rebaixo de localização (1,2 mm) —
  o pé em si (EPDM/silicone adesivo, `pe_altura` 10 mm) é externo.
- `arranjo_3d.FCStd` é snapshot, não assembly vivo (mesma limitação da
  versão em chapa).
