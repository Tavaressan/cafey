"""Constroi mechanical/build/peca2.FCStd do zero.

Peca 2 do pedestal: fundo plano removivel, sem nenhuma dobra. Parafusado na FACE
INFERIOR das abas de retorno da Peca 1 (tampa por baixo) - assim o fundo sai por
baixo para manutencao / troca do fusivel, sem ficar preso pelas abas viradas
para dentro. Rente a' face externa das saias (pegada_x x pegada_y). Protrai
espessura_fundo abaixo da borda das saias.

Mesmo sistema de coordenadas da Peca 1 (face superior do tampo em Z=0).
Dirigido pelo Spreadsheet `params` via expressao.

Uso: freecadcmd mechanical/scripts/build_peca2.py
"""

import os
import sys

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
import _env
from _build import build_spreadsheet, box, cyl

import FreeCAD as App
import Part

P = "params."
# fundo encostado por baixo: face superior em Z = -altura_externa (coplanar com a
# face inferior das abas e a borda das saias), face inferior em -altura_externa
# - espessura_fundo.
Z_FUNDO_BASE = "-%saltura_externa - %sespessura_fundo - 1" % (P, P)
H_THRU = "%sespessura_fundo + 2" % P


def build(doc):
    plate = box(doc, "fundo",
                P + "pegada_x", P + "pegada_y", P + "espessura_fundo",
                "0", "0",
                "-%saltura_externa - %sespessura_fundo" % (P, P))

    cuts = []

    # --- 8 furos de PASSAGEM M3, coincidentes com as porcas-rebite das abas da
    #     Peca 1. O parafuso entra por baixo e rosqueia na porca-rebite da aba;
    #     por isso aqui e' folga M3 (furo_fixacao), nao o furo da porca-rebite.
    tf = "%sespessura + %saba_fundo / 2" % (P, P)
    tt = "%spegada_y - %sespessura - %saba_fundo / 2" % (P, P, P)
    td = "%spegada_x - %sespessura - %saba_fundo / 2" % (P, P, P)
    grupos = []
    for xe in ("%spegada_x / 3" % P, "2 * %spegada_x / 3" % P):
        grupos += [(xe, tf), (xe, tt)]
    for ye in ("%spegada_y / 3" % P, "2 * %spegada_y / 3" % P):
        grupos += [(tf, ye), (td, ye)]
    for i, (xe, ye) in enumerate(grupos, 1):
        cuts.append(cyl(doc, "fundo_furo_%d" % i,
                        P + "furo_fixacao / 2", H_THRU,
                        (xe, ye, Z_FUNDO_BASE), "Z"))

    # Sem recortes de canto: por baixo das abas nao ha' nada a aliviar (as abas
    # de canto da Peca 1 ficam acima do plano do fundo).

    # --- 2 furos das cantoneiras da divisoria (linha faixa_baixa) ---
    for i, xe in enumerate(("%spegada_x / 4" % P, "3 * %spegada_x / 4" % P), 1):
        cuts.append(cyl(doc, "cant_furo_%d" % i,
                        P + "furo_fixacao / 2", H_THRU,
                        (xe, "%sespessura + %sfaixa_baixa" % (P, P), Z_FUNDO_BASE), "Z"))

    doc.recompute()
    tool = doc.addObject("Part::MultiFuse", "fundo_recortes")
    tool.Shapes = cuts
    peca2 = doc.addObject("Part::Cut", "peca2")
    peca2.Base = plate
    peca2.Tool = tool
    doc.recompute()
    _env.apply_descriptions(doc)
    furos = doc.addObject("App::TextDocument", "notas_furos")
    furos.Label = "catalogo de furos"
    furos.Text = _env.NOTAS_FUROS_P2
    doc.recompute()
    return peca2


def main():
    if os.path.exists(_env.FCSTD2):
        os.remove(_env.FCSTD2)
    doc = App.newDocument("peca2")
    build_spreadsheet(doc)
    peca2 = build(doc)
    doc.recompute()

    shp = peca2.Shape
    bb = shp.BoundBox
    print("solidos:", len(shp.Solids), " valido:", shp.isValid())
    print("bbox   : %.1f x %.1f x %.1f mm" % (bb.XLength, bb.YLength, bb.ZLength))

    os.makedirs(_env.BUILD_DIR, exist_ok=True)
    doc.saveAs(_env.FCSTD2)
    Part.export([peca2], _env.STEP2)
    print("salvo  :", _env.FCSTD2)
    print("step   :", _env.STEP2)
    if not shp.isValid() or len(shp.Solids) != 1:
        raise SystemExit("geometria invalida ou fragmentada")


main()
