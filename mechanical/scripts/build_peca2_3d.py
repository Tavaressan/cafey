"""Constroi mechanical/build/peca2_3d.FCStd do zero (variante para impressao 3D).

Peca 2 do pedestal, versao FDM: fundo plano removivel, impresso, sem nenhuma
dobra (igual em espirito a build_peca2.py, so' a fixacao muda: furo de
passagem para parafuso M3 que rosqueia no insert termico da Peca 1, no lugar
da porca-rebite). Parafusado na FACE INFERIOR da flange da Peca 1 - sai por
baixo para manutencao / troca do fusivel.

Mesmo sistema de coordenadas da Peca 1 (face superior do tampo em Z=0).
Dirigido pelo Spreadsheet `params` via expressao.

Uso: freecadcmd mechanical/scripts/build_peca2_3d.py
"""

import os
import sys

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
import _env3d as _env
from _build import box, cyl

import FreeCAD as App
import Part

P = "params."
# fundo encostado por baixo: face superior em Z = -altura_externa (coplanar com
# a face inferior da flange e a borda das paredes), face inferior em
# -altura_externa - parede_fundo.
Z_FUNDO_BASE = "-%saltura_externa - %sparede_fundo - 1" % (P, P)
H_THRU = "%sparede_fundo + 2" % P


def build(doc):
    plate = box(doc, "fundo",
                P + "pegada_x", P + "pegada_y", P + "parede_fundo",
                "0", "0",
                "-%saltura_externa - %sparede_fundo" % (P, P))

    cuts = []

    g = _env.params_dict()
    pontos = _env.insert_holes(g)
    nomes = []
    for xc, yc in pontos:
        if abs(yc - g["parede"] - g["aba_fundo"] / 2.0) < 1e-6:
            nomes.append(("f", "a" if xc < g["pegada_x"] / 2 else "b"))
        elif abs(yc - (g["pegada_y"] - g["parede"] - g["aba_fundo"] / 2.0)) < 1e-6:
            nomes.append(("t", "a" if xc < g["pegada_x"] / 2 else "b"))
        elif abs(xc - g["parede"] - g["aba_fundo"] / 2.0) < 1e-6:
            nomes.append(("e", "a" if yc < g["pegada_y"] / 2 else "b"))
        else:
            nomes.append(("d", "a" if yc < g["pegada_y"] / 2 else "b"))

    for i, ((xc, yc), (lado, col)) in enumerate(zip(pontos, nomes), 1):
        cuts.append(cyl(doc, "fundo_furo_%d" % i,
                        P + "furo_passagem / 2", H_THRU,
                        ("%f" % xc, "%f" % yc, Z_FUNDO_BASE), "Z"))

    # --- 2 furos das cantoneiras da divisoria (linha faixa_baixa) ---
    for i, xe in enumerate(("%spegada_x / 4" % P, "3 * %spegada_x / 4" % P), 1):
        cuts.append(cyl(doc, "cant_furo_%d" % i,
                        P + "furo_passagem / 2", H_THRU,
                        (xe, "%sparede + %sfaixa_baixa" % (P, P), Z_FUNDO_BASE), "Z"))

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
    doc = App.newDocument("peca2_3d")
    _env.build_spreadsheet(doc)
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
