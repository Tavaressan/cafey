# mechanical — módulo físico (pedestal em aço inox)

Modelo paramétrico do pedestal de `docs/status-modulo-fisico.md`.

- **Peça 1** — peça dobrada (tampo + 4 saias + 4 abas de fundo + 4 abas de canto).
- **Peça 2** — fundo plano removível, apoiado sobre as abas de retorno.
- **Montagem** — as duas peças na posição de projeto.

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
freecadcmd mechanical/scripts/build_montagem.py  # build/montagem.FCStd + montagem.step (checa interferência)
freecadcmd mechanical/scripts/flatten_peca1.py   # build/peca1_plano.FCStd (contorno planificado)
freecadcmd mechanical/scripts/export_dxf.py      # build/peca1_plano.dxf + peca2_plano.dxf
```

Ordem: `build_peca1` → `build_peca2` → `build_montagem`; `flatten_peca1` →
`export_dxf`. `freecadcmd` = `/Applications/FreeCAD.app/Contents/Resources/bin/freecadcmd`
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
| `montagem.FCStd` / `montagem.step` | Conjunto (snapshot; interferência Peça1×Peça2 = 0) |
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

## Notas de fabricação

- **Ordem de dobra:** abas do fundo primeiro, com a chapa plana; saias depois. A
  viradeira não alcança o interior de uma caixa de 45 × 260 mm.
- Alívio de canto de `raio_dobra + espessura` (~2,4 mm) no encontro das dobras.
- Porta-fusível em linha preso por abraçadeira — nenhum furo em chapa.
- Alumínio em inox forma par galvânico; parafuso/rebite de inox (consulta 9).
