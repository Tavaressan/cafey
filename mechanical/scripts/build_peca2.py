"""Constroi mechanical/build/peca2.FCStd do zero.

Peca 2 do pedestal: fundo plano removivel, sem nenhuma dobra. Apoia sobre as
quatro abas de retorno da Peca 1. Mesmo sistema de coordenadas da Peca 1 (face
superior do tampo em Z=0). Dirigido pelo Spreadsheet `params` via expressao.

Uso: freecadcmd mechanical/scripts/build_peca2.py
"""

import os
import sys

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
import _env
from _build import build_spreadsheet, box, cyl

import FreeCAD as App

P = "params."
Z_FUNDO_BASE = "-%saltura_externa + %sespessura - 1" % (P, P)
H_THRU = "%sespessura_fundo + 2" % P


def build(doc):
    plate = box(doc, "fundo",
                "%spegada_x - 2 * %sespessura - 2 * %sfolga_fundo" % (P, P, P),
                "%spegada_y - 2 * %sespessura - 2 * %sfolga_fundo" % (P, P, P),
                P + "espessura_fundo",
                "%sespessura + %sfolga_fundo" % (P, P),
                "%sespessura + %sfolga_fundo" % (P, P),
                "-%saltura_externa + %sespessura" % (P, P))

    cuts = []

    # --- 8 furos M3, coincidentes com as porcas-rebite das abas da Peca 1 ---
    tf = "%sespessura + %saba_fundo / 2" % (P, P)
    tt = "%spegada_y - %sespessura - %saba_fundo / 2" % (P, P, P)
    td = "%spegada_x - %sespessura - %saba_fundo / 2" % (P, P, P)
    grupos = []
    for xe in ("%spegada_x / 3" % P, "2 * %spegada_x / 3" % P):
        grupos += [(xe, tf), (xe, tt)]
    for ye in ("%spegada_y / 3" % P, "2 * %spegada_y / 3" % P):
        grupos += [(tf, ye), (td, ye)]
    # furo de PASSAGEM do parafuso M3 (a porca-rebite fica na aba da Peca 1, nao
    # aqui): folga M3 = furo_fixacao, nao o furo grande da porca-rebite.
    for i, (xe, ye) in enumerate(grupos, 1):
        cuts.append(cyl(doc, "fundo_furo_%d" % i,
                        P + "furo_fixacao / 2", H_THRU,
                        (xe, ye, Z_FUNDO_BASE), "Z"))

    # --- 4 recortes de canto (alivio para as abas de canto da Peca 1) ---
    lx = "%sespessura + %sfolga_fundo - 1" % (P, P)          # x junto a' lateral esq
    rx = "%spegada_x - 2 * %sespessura - %sfolga_fundo" % (P, P, P)  # x junto a' direita
    fy = "%sespessura + %sfolga_fundo - 1" % (P, P)          # y junto a' saia frontal
    ty = "%spegada_y - %sespessura - %saba_canto - %sfolga_fundo" % (P, P, P, P)
    wx = "%sespessura + 1" % P
    wy = "%saba_canto + 1" % P
    for nm, xx, yy in [("notch_fl", lx, fy), ("notch_fr", rx, fy),
                       ("notch_tl", lx, ty), ("notch_tr", rx, ty)]:
        cuts.append(box(doc, nm, wx, wy, H_THRU, xx, yy, Z_FUNDO_BASE))

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
    print("salvo  :", _env.FCSTD2)
    if not shp.isValid() or len(shp.Solids) != 1:
        raise SystemExit("geometria invalida ou fragmentada")


main()
