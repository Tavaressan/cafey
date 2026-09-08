# pedestal — modelos cadgen (build123d)

Réplica em `cadgen`/`build123d` da **variante impressa em 3D** do pedestal
(`mechanical/scripts/*_3d.py`, FreeCAD), para comparação independente de kernel.
Mesmo sistema de coordenadas do FreeCAD: canto em `(0,0)`, face superior do
tampo em `Z=0`, peça em `Z<0`.

| Script | Artefato | Descrição | Equivalente FreeCAD |
|---|---|---|---|
| `corpo.py` | `STEP/corpo.step` | Peça 1: tampo + paredes + flange + bosses + reforço + aberturas | `build_peca1_3d.py` |
| `tampa.py` | `STEP/tampa.step` | Peça 2: fundo + furos M3 + cantoneiras + assentos de pé | `build_peca2_3d.py` |
| `divisoria.py` | `STEP/divisoria.step` | Divisória de policarbonato + rasgo de passa-fio | (parte de `build_arranjo_3d.py`) |
| `arranjo.py` | `STEP/arranjo.step` | Raiz: corpo + tampa + divisória + 13 volumes de referência | `build_arranjo_3d.py` |

`lib/params.py` transcreve `mechanical/params/parametros_3d.csv` +
`componentes_3d.csv` como constantes (o gate do cadgen não lê arquivos) —
**manter em sincronia à mão**. `lib/geometry.py` são os helpers (`cbox` ancora
no canto mínimo como o `Part::Box`; `insert_xy`/`column_xy`/`vent_grid`
espelham `_env3d`).

## Uso

```bash
# venv Python 3.11 (cadgen exige >= 3.11; o python do sistema é 3.10):
uv venv --python python3.11 .venv
uv pip install --python .venv/bin/python "cadgen[snapshot]==0.5.0"

.venv/bin/python src/arranjo.py                 # constrói o root e os 3 filhos
.venv/bin/python -m cadgen.cli step inspect validate STEP/arranjo.step
.venv/bin/python -m cadgen.cli step inspect interfere STEP/arranjo.step --tolerance 0.5
.venv/bin/python -m cadgen.cli step inspect diff STEP/corpo.step ../build/peca1_3d.step
```

`cadgen step snapshot` **não funciona** neste host (Playwright não suporta
Chromium em macOS 12) — revisão visual fica pelo CAD Viewer ou por outro host.
