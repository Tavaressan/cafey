"""Exporta os contornos planos para DXF de corte.

- peca2_plano.dxf : Peca 2 completa (contorno + 8 furos M3 + 2 furos de
  cantoneira + 4 recortes de canto). Exata - a peca ja' e' plana.
- peca1_plano.dxf : contorno externo da Peca 1 planificada (sem aberturas de
  face; ver flatten_peca1.py e a tabela no README).

Rode flatten_peca1.py e build_peca2.py antes.
Uso: freecadcmd mechanical/scripts/export_dxf.py
"""

import os
import sys

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
import _env

import FreeCAD as App
import Part
import importDXF


def _flat_face_of_solid(shp):
    """Face superior de um solido de chapa, transladada para Z=0."""
    top = max(shp.Faces, key=lambda f: f.CenterOfMass.z
              if abs(f.normalAt(0, 0).z) > 0.9 else -1e9)
    return top.translated(App.Vector(0, 0, -top.CenterOfMass.z))


def main():
    for p in (_env.PLANO1_FCSTD, _env.FCSTD2):
        if not os.path.exists(p):
            raise SystemExit("falta %s - rode flatten_peca1.py / build_peca2.py" % p)

    doc = App.newDocument("dxf_tmp")

    d1 = App.openDocument(_env.PLANO1_FCSTD)
    f1 = doc.addObject("Part::Feature", "peca1_plano")
    f1.Shape = d1.getObject("peca1_plano").Shape.copy()
    App.closeDocument(d1.Name)

    d2 = App.openDocument(_env.FCSTD2)
    f2 = doc.addObject("Part::Feature", "peca2_plano")
    f2.Shape = _flat_face_of_solid(d2.getObject("peca2").Shape)
    App.closeDocument(d2.Name)

    doc.recompute()
    importDXF.export([f1], _env.DXF1)
    importDXF.export([f2], _env.DXF2)
    for path in (_env.DXF1, _env.DXF2):
        print("exportado: %s (%d bytes)" % (path, os.path.getsize(path)))


main()
