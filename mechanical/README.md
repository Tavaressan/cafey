# mechanical — módulo físico (pedestal em aço inox)

Modelo paramétrico da **Peça 1** (peça dobrada) de `docs/status-modulo-fisico.md`.
Peça 2 (fundo plano removível) ainda não modelada.

## Abordagem

Sólido `Part` paramétrico, 100% por script headless, dirigido pelo Spreadsheet
`params`. Sem o addon SheetMetal nesta fase: a planificação é analítica
(`bend deduction = 2·setback − BA`), auditável e independente do unfolder frágil.
O SheetMetal fica reservado para quando o revisor da empresa exigir árvore
sheet-metal nativa ou unfold validado pela ferramenta dele.

Todos os parâmetros de processo (`espessura`, `raio_dobra`, `fator_k`, `material`)
são **placeholders arbitrados** — é a planilha `params` que o revisor corrige, e a
geometria depende dela por expressão.

## Coordenadas

Face superior do tampo em `Z = 0`; a peça desce para `Z < 0`. `X` ao longo de
`pegada_x`, `Y` ao longo de `pegada_y`. `Y = 0` é a saia frontal (baixa tensão),
`Y = pegada_y` a traseira (rede). `X = 0` é a lateral esquerda.

## Scripts

```
freecadcmd mechanical/scripts/build_peca1.py    # gera build/peca1.FCStd
freecadcmd mechanical/scripts/unfold_peca1.py   # tabela de dobras -> Spreadsheet 'planificacao'
freecadcmd mechanical/scripts/check_peca1.py    # validação geométrica (sai !=0 se falhar)
freecadcmd mechanical/scripts/export_step.py    # gera build/peca1.step
```

`freecadcmd` = `/Applications/FreeCAD.app/Contents/Resources/bin/freecadcmd`
(FreeCAD 1.1.3). `parametros.csv` é a fonte legível; `build_peca1.py` a transcreve
para o Spreadsheet.

Cada objeto do FCStd carrega descrição: `Label` legível + `Label2` (campo
"Descrição" no painel de propriedades) com a razão da peça e a decisão de
projeto. As descrições ficam em `_env.py` (`DESCRIPTIONS`). O objeto
`notas_fabricacao` (App::TextDocument) reúne ordem de dobra, fixação e o resto
das notas que não são geometria.

## Estado / limitações desta versão

- Planificação calculada: **365,4 × 315,4 mm** (documento: ~366 × 316). Chapa
  bruta sugerida ~400 × 350 mm.
- **Sem raios de dobra** no sólido (arestas vivas). São cosméticos para o STEP; a
  linha neutra real vive na tabela analítica. Adicionar `fillet` numa próxima
  iteração se o revisor pedir o STEP com raio.
- `usb_w`, `usb_h`: placeholders — medir o flange do conector de painel (item 16
  da lista de verificação).
- Grade de rasgos: 5 posições em `Y` entre as margens de extremidade. Na lateral
  esquerda o recorte USB ocupa a posição mais próxima de `pegada_y − usb_y`,
  deixando 4 rasgos; a direita tem os 5. Assimetria proposital — não corrigir.
- Furação de canto: 2 furos por canto em `Z` a 1/3 e 2/3 da altura da saia
  (posição provisória).

## Notas de fabricação (não vão na geometria)

- **Ordem de dobra:** abas do fundo primeiro, com a chapa plana; saias depois. A
  viradeira não alcança o interior de uma caixa de 45 × 260 mm.
- Porta-fusível em linha preso por abraçadeira — nenhum furo em chapa.
- Alívio de canto de `raio_dobra + espessura` (~2,4 mm) no encontro das dobras.
