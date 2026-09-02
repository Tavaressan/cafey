"""Blank plano (planificacao) da Peca 1 como face 2D em Z=0.

Construcao analitica a partir de _env.flat_segments: nao usa o unfolder do
SheetMetal. Entrega o CONTORNO EXTERNO + alivios de canto + abas de canto.

As aberturas por face NAO entram no blank: a posicao planificada delas depende
do fator K real da ferramenta (consulta 6/7 da lista de verificacao). Elas vao
no desenho cotado / no unfold da empresa. Ver tabela de centros no README.

Uso: freecadcmd mechanical/scripts/flatten_peca1.py
"""

import os
import sys

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
import _env

import FreeCAD as App
import Part


def _rect(x0, x1, y0, y1):
    return Part.makeBox(x1 - x0, y1 - y0, 1.0, App.Vector(x0, y0, 0))


def main():
    g = _env.params_dict()
    fs = _env.flat_segments(g)
    cx, cy = fs["cumX"], fs["cumY"]
    flat_x, flat_y = cx[-1], cy[-1]
    ac_flat = g["aba_canto"] - fs["sb"]          # aba de canto planificada
    rel = g["alivio_canto"]

    solidos = [
        _rect(0, flat_x, cy[4], cy[5]),           # barra horizontal (tampo + saias/abas lat.)
        _rect(cx[4], cx[5], 0, flat_y),           # barra vertical  (tampo + saias/abas fr/tr)
        _rect(cx[4] - ac_flat, cx[4], cy[2], cy[3]),   # aba de canto frontal-esq
        _rect(cx[5], cx[5] + ac_flat, cy[2], cy[3]),   # aba de canto frontal-dir
        _rect(cx[4] - ac_flat, cx[4], cy[6], cy[7]),   # aba de canto traseira-esq
        _rect(cx[5], cx[5] + ac_flat, cy[6], cy[7]),   # aba de canto traseira-dir
    ]
    blank = solidos[0].multiFuse(solidos[1:])
    for px, py in [(cx[4], cy[4]), (cx[5], cy[4]), (cx[4], cy[5]), (cx[5], cy[5])]:
        blank = blank.cut(Part.makeBox(rel, rel, 3.0,
                                       App.Vector(px - rel / 2, py - rel / 2, -1)))
    blank = blank.removeSplitter()

    face = sorted((f for f in blank.Faces
                   if abs(f.CenterOfMass.z) < 1e-6 and f.normalAt(0, 0).z < 0),
                  key=lambda f: -f.Area)[0]

    if os.path.exists(_env.PLANO1_FCSTD):
        os.remove(_env.PLANO1_FCSTD)
    doc = App.newDocument("peca1_plano")
    feat = doc.addObject("Part::Feature", "peca1_plano")
    feat.Shape = face
    feat.Label = "Peca 1 - planificacao (contorno)"
    try:
        feat.Label2 = ("Contorno + alivios + abas de canto. Sem aberturas: "
                       "posicao planificada depende do fator K da ferramenta.")
    except AttributeError:
        pass
    doc.recompute()

    bb = face.BoundBox
    print("blank : %.2f x %.2f mm   area %.0f mm2" % (bb.XLength, bb.YLength, face.Area))
    print("doc   : ~366 x 316 mm   chapa bruta ~400 x 350 mm")
    os.makedirs(_env.BUILD_DIR, exist_ok=True)
    doc.saveAs(_env.PLANO1_FCSTD)
    print("salvo :", _env.PLANO1_FCSTD)


main()
