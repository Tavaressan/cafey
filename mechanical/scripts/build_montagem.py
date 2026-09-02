"""Monta Peca 1 + Peca 2 num unico documento e exporta STEP.

As duas pecas usam o mesmo sistema de coordenadas (face superior do tampo em
Z=0), entao a Peca 2 ja' entra na posicao de projeto sem deslocamento: apoiada
sobre as abas de retorno, base em Z = -altura_externa + espessura.

Snapshot: copia as formas dos FCStd das pecas (nao e' um assembly vivo).
Rode build_peca1.py e build_peca2.py antes.

Uso: freecadcmd mechanical/scripts/build_montagem.py
"""

import os
import sys

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
import _env

import FreeCAD as App
import Part


def _load_shape(path, objname):
    src = App.openDocument(path)
    shp = src.getObject(objname).Shape.copy()
    App.closeDocument(src.Name)
    return shp


def main():
    for p in (_env.FCSTD, _env.FCSTD2):
        if not os.path.exists(p):
            raise SystemExit("falta %s - rode build_peca1.py / build_peca2.py" % p)

    doc = App.newDocument("montagem")
    for nm, path, obj in [("asm_peca1", _env.FCSTD, "peca1"),
                          ("asm_peca2", _env.FCSTD2, "peca2")]:
        feat = doc.addObject("Part::Feature", nm)
        feat.Shape = _load_shape(path, obj)
    _env.apply_descriptions(doc)
    doc.recompute()

    objs = [doc.getObject("asm_peca1"), doc.getObject("asm_peca2")]
    inter = objs[0].Shape.common(objs[1].Shape)
    print("interferencia Peca1 x Peca2: %.3f mm3" % inter.Volume)

    os.makedirs(_env.BUILD_DIR, exist_ok=True)
    doc.saveAs(_env.MONTAGEM_FCSTD)
    Part.export(objs, _env.MONTAGEM_STEP)
    print("salvo  :", _env.MONTAGEM_FCSTD)
    print("step   :", _env.MONTAGEM_STEP)
    if inter.Volume > 1.0:
        raise SystemExit("Peca 1 e Peca 2 se interpenetram (%.1f mm3)" % inter.Volume)


main()
